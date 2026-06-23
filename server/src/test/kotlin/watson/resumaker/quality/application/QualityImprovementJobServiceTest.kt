package watson.resumaker.quality.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.quality.domain.Finding
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityReport
import watson.resumaker.quality.domain.SuggestionGuide
import watson.resumaker.quality.domain.TreatmentKind
import watson.resumaker.quality.infrastructure.QualityCandidateRepository
import watson.resumaker.quality.infrastructure.QualityImprovementJobRepository
import watson.resumaker.quality.presentation.QualityImprovementJobMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * [QualityImprovementJobService] 단위 테스트(접수). 진단·레포는 mock.
 *
 * 검증: 요청 소견 중 **AUTO_REWRITE만** 접수해 PENDING 작업을 만든다(개선 제안·잘못된 식별자는 제외), 자동 적용
 * 가능한 소견이 없으면 거부(400).
 */
class QualityImprovementJobServiceTest {

    private val reviewService: QualityReviewService = mock()
    private val jobRepository: QualityImprovementJobRepository = mock()
    private val candidateRepository: QualityCandidateRepository = mock()
    private val mapper = QualityImprovementJobMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)
    private val service = QualityImprovementJobService(reviewService, jobRepository, candidateRepository, mapper, clock)

    private val ownerId = UserId(UUID.randomUUID())
    private val artifactId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val autoSectionId = SectionId(UUID.randomUUID())
    private val suggestSectionId = SectionId(UUID.randomUUID())

    private val autoFindingId = "${autoSectionId.value}:I1"
    private val suggestFindingId = "${suggestSectionId.value}:I4"

    private fun report() = QualityReport(
        artifactId = artifactId,
        versionId = versionId,
        findings = listOf(
            Finding(autoFindingId, autoSectionId, "section-0-요약", QualityCriterion.STRONG_VERB, TreatmentKind.AUTO_REWRITE, "담당했다"),
            Finding(suggestFindingId, suggestSectionId, "section-1-경력", QualityCriterion.VAGUE_METRIC, TreatmentKind.SUGGESTION, "대용량", SuggestionGuide("구체 값을 적어 주세요.")),
        ),
    )

    @Test
    fun AUTO_REWRITE_소견만_접수해_PENDING_작업을_만든다() {
        // given — 사용자가 자동 적용 소견과 개선 제안 소견을 모두 골랐다.
        whenever(reviewService.review(ownerId, artifactId)).thenReturn(report())
        whenever(jobRepository.save(any<QualityImprovementJob>())).thenAnswer { it.arguments[0] }

        // when
        val response = service.submit(ownerId, artifactId, listOf(autoFindingId, suggestFindingId))

        // then — 작업엔 AUTO_REWRITE 소견만 담긴다(개선 제안은 처치 작업을 거치지 않음).
        val captor = argumentCaptor<QualityImprovementJob>()
        verify(jobRepository).save(captor.capture())
        assertThat(captor.firstValue.findingIds).containsExactly(autoFindingId)
        assertThat(captor.firstValue.versionId).isEqualTo(versionId)
        assertThat(response.status.name).isEqualTo("PENDING")
    }

    @Test
    fun 자동적용_가능한_소견이_없으면_접수를_거부한다() {
        // given — 사용자가 개선 제안 소견만 골랐다(자동 적용 0건).
        whenever(reviewService.review(ownerId, artifactId)).thenReturn(report())

        // when and then — 400(DomainValidationException), 작업 미생성.
        assertThatThrownBy { service.submit(ownerId, artifactId, listOf(suggestFindingId)) }
            .isInstanceOf(DomainValidationException::class.java)
        verify(jobRepository, never()).save(any())
    }
}
