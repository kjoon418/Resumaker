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
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.quality.domain.Finding
import watson.resumaker.quality.domain.QualityCandidate
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobStatus
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
    private val quotaGuard: watson.resumaker.generation.application.GenerationQuotaGuard = mock()
    private val mapper = QualityImprovementJobMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)
    private val service = QualityImprovementJobService(reviewService, jobRepository, candidateRepository, quotaGuard, mapper, clock)

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

    @Test
    fun 일일_한도를_넘으면_접수_사전점검에서_막힌다() {
        // given (§5.1-3) — 접수 사전 점검이 한도 초과로 던진다(진단·작업 생성에 도달하지 않는다).
        whenever(quotaGuard.checkQualityImprovement(ownerId))
            .thenThrow(QuotaExceededException("한도 초과", code = "QUALITY_IMPROVEMENT_QUOTA_EXCEEDED"))

        // when and then
        assertThatThrownBy { service.submit(ownerId, artifactId, listOf(autoFindingId)) }
            .isInstanceOf(QuotaExceededException::class.java)
        verify(reviewService, never()).review(any(), any())
        verify(jobRepository, never()).save(any())
    }

    @Test
    fun latestFor_최신_작업을_매핑하고_제외_항목수를_계산해_돌려준다() {
        // given (§3) — 서로 다른 두 항목(s1,s2)을 요청했고 후보는 1개만 성공 → 제외 1(원본 유지 고지용).
        val s1 = SectionId(UUID.randomUUID())
        val s2 = SectionId(UUID.randomUUID())
        val job = QualityImprovementJob.create(
            ownerId, artifactId, versionId,
            listOf("${s1.value}:I1", "${s2.value}:I1"),
            Instant.parse("2026-06-22T00:00:00Z"),
        )
        job.markSucceeded(Instant.parse("2026-06-22T00:01:00Z"))
        whenever(jobRepository.findFirstByArtifactIdAndOwnerIdOrderByCreatedAtDesc(artifactId, ownerId)).thenReturn(job)
        whenever(candidateRepository.findAllByJobId(job.id.value)).thenReturn(
            listOf(QualityCandidate.create(job.id.value, s1, "section-0-요약", "원본", "다듬음", listOf("I1"))),
        )

        // when
        val response = service.latestFor(ownerId, artifactId)

        // then
        assertThat(response).isNotNull
        assertThat(response!!.status).isEqualTo(QualityImprovementJobStatus.SUCCEEDED)
        assertThat(response.excludedSectionCount).isEqualTo(1)
    }

    @Test
    fun latestFor_작업이_없으면_null() {
        // given — 이 산출물에 개선 작업이 없다(이력서가 아니거나 아직 접수 안 함).
        whenever(jobRepository.findFirstByArtifactIdAndOwnerIdOrderByCreatedAtDesc(artifactId, ownerId)).thenReturn(null)

        // when and then — null(컨트롤러가 204).
        assertThat(service.latestFor(ownerId, artifactId)).isNull()
    }

    @Test
    fun dismiss_작업과_후보를_지운다() {
        // given — 진행 카드 "닫기"로 작업을 치운다.
        val job = QualityImprovementJob.create(
            ownerId, artifactId, versionId, listOf("${autoSectionId.value}:I1"), Instant.parse("2026-06-22T00:00:00Z"),
        )
        whenever(jobRepository.findByIdAndOwnerId(job.id, ownerId)).thenReturn(job)

        // when
        service.dismiss(ownerId, artifactId, job.id)

        // then
        verify(candidateRepository).deleteByJobId(job.id.value)
        verify(jobRepository).delete(job)
    }

    @Test
    fun dismiss_경로_산출물과_작업이_불일치하면_404() {
        // given (QC8) — 작업이 다른 산출물 것이면 경로 불일치로 거부(삭제하지 않음).
        val otherArtifactId = UUID.randomUUID()
        val job = QualityImprovementJob.create(
            ownerId, otherArtifactId, versionId, listOf("${autoSectionId.value}:I1"), Instant.parse("2026-06-22T00:00:00Z"),
        )
        whenever(jobRepository.findByIdAndOwnerId(job.id, ownerId)).thenReturn(job)

        // when and then
        assertThatThrownBy { service.dismiss(ownerId, artifactId, job.id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
        verify(jobRepository, never()).delete(any())
    }
}
