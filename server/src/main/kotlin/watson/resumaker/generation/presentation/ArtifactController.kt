package watson.resumaker.generation.presentation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.generation.application.ArtifactGenerationService
import watson.resumaker.generation.application.ArtifactReadService
import watson.resumaker.generation.application.SectionRegenerationService
import java.util.UUID

/**
 * 산출물 API(Cycle D).
 * - POST /artifacts/resume    : 이력서 1차 생성. 부분 실패 버전도 200으로 내려간다(도메인 이해 §306).
 * - POST /artifacts/portfolio : 포트폴리오 1차 생성(경험당 항목 1개).
 * - GET  /artifacts/{id}      : 열람(수용 기준 12). 활성 버전 전체/항목 텍스트·상태·출처 반환. 복사는 클라이언트.
 * - POST /artifacts/{artifactId}/sections/{sectionId}/regenerate : 항목 단위 재생성(수용 기준 10·19·20).
 *   해당 항목만 다시 만들어 새 활성 버전을 만들고, 갱신된 산출물(활성 버전)을 돌려준다. 동시 중복은 409.
 *
 * 모든 엔드포인트는 인증 주체 ownerId로 소유 격리한다(CurrentUserProvider). 생성 엔드포인트는 정적 경로
 * (`/resume`, `/portfolio`)이고 열람은 경로 변수(`/{id}`)지만 HTTP 메서드가 달라 충돌하지 않는다. 재생성은 더
 * 깊은 정적 세그먼트(`/sections/{sectionId}/regenerate`)라 `/{id}` GET과 충돌하지 않는다. 정적 경로를 먼저
 * 선언해 매핑 의도를 분명히 한다(기존 컨벤션).
 */
@RestController
@RequestMapping("/artifacts")
class ArtifactController(
    private val generationService: ArtifactGenerationService,
    private val readService: ArtifactReadService,
    private val regenerationService: SectionRegenerationService,
    private val mapper: GenerationMapper,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping("/resume")
    fun generateResume(@Valid @RequestBody request: ResumeGenerationRequest): ResponseEntity<GenerationResponse> {
        val response = generationService.generateResume(
            currentUserProvider.currentUserId(),
            mapper.toResumeCommand(request),
        )
        return generationResult(response)
    }

    @PostMapping("/portfolio")
    fun generatePortfolio(
        @Valid @RequestBody request: PortfolioGenerationRequest,
    ): ResponseEntity<GenerationResponse> {
        val response = generationService.generatePortfolio(
            currentUserProvider.currentUserId(),
            mapper.toPortfolioCommand(request),
        )
        return generationResult(response)
    }

    @GetMapping("/{id}")
    fun getArtifact(@PathVariable id: String): ResponseEntity<ArtifactResponse> =
        ResponseEntity.ok(readService.getArtifact(currentUserProvider.currentUserId(), toId(id)))

    /**
     * 항목 단위 재생성(도메인 이해 §5, 수용 기준 10·19·20). 해당 항목만 다시 만들어 새 활성 버전을 만들고,
     * 갱신된 산출물(활성 버전)을 200으로 돌려준다. 같은 항목 동시 재생성은 서비스가 409(CONFLICT)로 거절한다.
     */
    @PostMapping("/{artifactId}/sections/{sectionId}/regenerate")
    fun regenerateSection(
        @PathVariable artifactId: String,
        @PathVariable sectionId: String,
        @Valid @RequestBody request: RegenerateSectionRequest,
    ): ResponseEntity<ArtifactResponse> {
        val response = regenerationService.regenerateSection(
            currentUserProvider.currentUserId(),
            mapper.toRegenerateSectionCommand(artifactId, sectionId, request),
        )
        return ResponseEntity.ok(response)
    }

    /**
     * 생성 응답의 HTTP 상태를 결정한다(도메인 이해 §306). 모든 항목이 GENERATED면 새 산출물 생성으로 201,
     * 하나라도 실패(*_FAILED) 항목이 섞인 부분 성공 버전이면 200으로 내려 클라이언트가 재시도 UX를 띄우게 한다.
     */
    private fun generationResult(response: GenerationResponse): ResponseEntity<GenerationResponse> {
        val allGenerated = response.sections.all { it.status == SectionStatus.GENERATED }
        val status = if (allGenerated) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(response)
    }

    private fun toId(id: String): ArtifactId = ArtifactId(UUID.fromString(id))
}
