package watson.resumaker.quality.presentation

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.quality.application.QualityReviewService
import java.util.UUID

/**
 * 이력서 자동 품질 개선 API(품질 개선 기획 §3.4 온디맨드 2단계). 전부 `/artifacts/{artifactId}` 하위에 두고
 * ownerId로 소유 격리한다(QC8). 포트폴리오는 서비스단 RESUME 가드로 거절한다(QC10).
 *
 * - POST /artifacts/{artifactId}/quality-review : 품질 점검(진단). 무차감·동기. 개선 소견 목록을 200으로 돌려준다.
 *
 * (처치 접수·조회·채택 엔드포인트는 후속 커밋에서 더한다.)
 */
@RestController
@RequestMapping("/artifacts/{artifactId}")
class QualityController(
    private val reviewService: QualityReviewService,
    private val mapper: QualityMapper,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping("/quality-review")
    fun review(@PathVariable artifactId: String): ResponseEntity<QualityReviewResponse> {
        val report = reviewService.review(currentUserProvider.currentUserId(), toUuid(artifactId))
        return ResponseEntity.ok(mapper.toResponse(report))
    }

    private fun toUuid(value: String): UUID = try {
        UUID.fromString(value)
    } catch (e: IllegalArgumentException) {
        throw DomainValidationException("입력 형식을 다시 확인해 주세요.")
    }
}
