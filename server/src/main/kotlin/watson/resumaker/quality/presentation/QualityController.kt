package watson.resumaker.quality.presentation

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
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.quality.application.QualityImprovementJobService
import watson.resumaker.quality.application.QualityReviewService
import watson.resumaker.quality.domain.QualityImprovementJobId
import java.util.UUID

/**
 * 이력서 자동 품질 개선 API(품질 개선 기획 §3.4 온디맨드 2단계). 전부 `/artifacts/{artifactId}` 하위에 두고
 * ownerId로 소유 격리한다(QC8). 포트폴리오는 서비스단 RESUME 가드로 거절한다(QC10).
 *
 * - POST /artifacts/{artifactId}/quality-review : 품질 점검(진단). 무차감·동기. 개선 소견 목록을 200으로 돌려준다.
 * - POST /artifacts/{artifactId}/quality-improvements : 처치(품질 개선) **접수**. AUTO_REWRITE 소견만 받아 즉시
 *   202+jobId를 돌려주고 워커가 백그라운드로 후보를 만든다. 클라이언트는 {jobId}로 완료를 폴링한다.
 * - GET  /artifacts/{artifactId}/quality-improvements/{jobId} : 작업 단건 조회(상태·후보 폴링).
 *
 * (후보 채택 엔드포인트는 후속 커밋에서 더한다.)
 */
@RestController
@RequestMapping("/artifacts/{artifactId}")
class QualityController(
    private val reviewService: QualityReviewService,
    private val improvementJobService: QualityImprovementJobService,
    private val mapper: QualityMapper,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping("/quality-review")
    fun review(@PathVariable artifactId: String): ResponseEntity<QualityReviewResponse> {
        val report = reviewService.review(currentUserProvider.currentUserId(), toUuid(artifactId))
        return ResponseEntity.ok(mapper.toResponse(report))
    }

    @PostMapping("/quality-improvements")
    fun submitImprovement(
        @PathVariable artifactId: String,
        @Valid @RequestBody request: QualityImprovementRequest,
    ): ResponseEntity<QualityImprovementJobResponse> {
        val job = improvementJobService.submit(
            currentUserProvider.currentUserId(),
            toUuid(artifactId),
            request.findingIds!!,
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job)
    }

    @GetMapping("/quality-improvements/{jobId}")
    fun getImprovement(
        @PathVariable artifactId: String,
        @PathVariable jobId: String,
    ): ResponseEntity<QualityImprovementJobResponse> =
        ResponseEntity.ok(
            improvementJobService.get(
                currentUserProvider.currentUserId(),
                QualityImprovementJobId(toUuid(jobId)),
            ),
        )

    private fun toUuid(value: String): UUID = try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw DomainValidationException("입력 형식을 다시 확인해 주세요.")
    }
}
