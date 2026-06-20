package watson.resumaker.generation.presentation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.generation.application.ArtifactGenerationService
import watson.resumaker.generation.application.ArtifactReadService
import watson.resumaker.generation.application.SectionEditService
import watson.resumaker.generation.application.SectionRegenerationService
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.generation.application.VersionRestoreService
import java.util.UUID

/**
 * 산출물 API(Cycle D).
 * - POST /artifacts/resume    : 이력서 1차 생성. 부분 실패 버전도 200으로 내려간다(도메인 이해 §306).
 * - POST /artifacts/portfolio : 포트폴리오 1차 생성(경험당 항목 1개).
 * - GET  /artifacts/{id}      : 열람(수용 기준 12). 활성 버전 전체/항목 텍스트·상태·출처 반환. 복사는 클라이언트.
 * - POST /artifacts/{artifactId}/sections/{sectionId}/regenerate : 항목 단위 재생성(수용 기준 10·19·20).
 *   해당 항목만 다시 만들어 새 활성 버전을 만들고, 갱신된 산출물(활성 버전)을 돌려준다. 동시 중복은 409.
 * - PUT  /artifacts/{artifactId}/sections/{sectionId}/content : 항목 직접 편집(수용 기준 10·19, §267).
 *   사용자가 항목 텍스트를 직접 수정해 새 활성 버전을 만들고, 갱신된 산출물(활성 버전)을 돌려준다.
 *   직접 편집에는 자동 검증을 적용하지 않는다(§428) — AI 비호출 순수 동기 영속.
 * - GET  /artifacts/{artifactId}/versions : 버전 목록 조회(수용 기준 11·12, §271~283·§363). 모든 버전을
 *   생성 순서로, 각 버전의 항목·활성여부·생성시각과 함께 비교용으로 내려준다. 비교는 definitionKey로 클라이언트가 한다.
 * - POST /artifacts/{artifactId}/versions/{versionId}/restore : 버전 복원(§277·§283, "복원 = 활성 전환").
 *   고른 이전 버전을 활성으로 되돌려 갱신된 산출물(활성 버전)을 돌려준다. 새 버전은 만들지 않는다.
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
    private val editService: SectionEditService,
    private val restoreService: VersionRestoreService,
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
     * 버전 목록 조회(수용 기준 11·12, 도메인 이해 §271~283·§363). 한 산출물의 모든 버전을 생성 순서로, 각 버전의
     * 항목·활성여부·생성시각과 함께 비교용으로 200으로 돌려준다. 클라이언트는 definitionKey로 버전 간 항목을 맞춰
     * 비교한다(§363: 같은 항목 = 같은 섹션 정의). 타인 소유·미존재 산출물은 404로 매핑된다(소유 격리).
     */
    @GetMapping("/{artifactId}/versions")
    fun getVersions(@PathVariable artifactId: String): ResponseEntity<ArtifactVersionsResponse> =
        ResponseEntity.ok(readService.getVersions(currentUserProvider.currentUserId(), toId(artifactId)))

    /**
     * 버전 복원(도메인 이해 §277·§283, "복원 = 활성 전환"). 고른 이전 버전을 활성으로 되돌려 갱신된 산출물
     * (활성 버전)을 200으로 돌려준다. 새 버전을 만들지 않으므로 이전 버전들이 그대로 보존된다. 타인 소유·미존재
     * 산출물·버전은 404로 매핑된다(소유 격리).
     */
    @PostMapping("/{artifactId}/versions/{versionId}/restore")
    fun restoreVersion(
        @PathVariable artifactId: String,
        @PathVariable versionId: String,
    ): ResponseEntity<ArtifactResponse> {
        val response = restoreService.restoreVersion(
            currentUserProvider.currentUserId(),
            mapper.toRestoreVersionCommand(artifactId, versionId),
        )
        return ResponseEntity.ok(response)
    }

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
     * 항목 직접 편집(도메인 이해 §5·§267·§271, 수용 기준 10·19). 사용자가 항목 텍스트를 직접 수정해 새 활성
     * 버전을 만들고, 갱신된 산출물(활성 버전)을 200으로 돌려준다. 직접 편집에는 자동 검증을 적용하지 않으며(§428)
     * AI 호출이 없는 순수 동기 영속이다. 빈 내용은 400(Bean Validation), 타인/미존재 산출물·항목은 404로 매핑된다.
     */
    @PutMapping("/{artifactId}/sections/{sectionId}/content")
    fun editSectionContent(
        @PathVariable artifactId: String,
        @PathVariable sectionId: String,
        @Valid @RequestBody request: EditSectionContentRequest,
    ): ResponseEntity<ArtifactResponse> {
        val response = editService.editSectionContent(
            currentUserProvider.currentUserId(),
            mapper.toEditSectionContentCommand(artifactId, sectionId, request),
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

    private fun toId(id: String): ArtifactId = try {
        ArtifactId(UUID.fromString(id))
    } catch (e: IllegalArgumentException) {
        throw DomainValidationException("입력 형식을 다시 확인해 주세요.")
    }
}
