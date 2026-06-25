package watson.resumaker.experience.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.domain.ExperienceReviewCriterion
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.ExperienceType
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.application.FactTokenExtractor
import watson.resumaker.quality.infrastructure.QualityCriteriaDictionary
import watson.resumaker.quality.infrastructure.QualityCriteriaProperties
import java.util.UUID

/**
 * [ExperienceReviewService] 단위 테스트(경험 점검 — 결정적 보강 유도). 검사기·추출기는 실제 구현을 쓰고(순수·결정적),
 * 소유 격리 경로만 레포를 mock한다.
 *
 * 검증: 모호수치-무번호→소견 / 모호수치-유번호→억제(이력서가 자동 객관화 가능) / 결과누락→소견 / 빈약본문→소견 /
 * 깨끗→0건(긍정) / 소유 격리 404.
 */
class ExperienceReviewServiceTest {

    private val repository: ExperienceRecordRepository = mock()
    private val criteria = QualityCriteriaDictionary(QualityCriteriaProperties())
    private val extractor = FactTokenExtractor()
    private val service = ExperienceReviewService(repository, criteria, extractor)

    private val ownerId = UserId(UUID.randomUUID())
    private val expId = ExperienceRecordId(UUID.randomUUID())

    private fun experience(body: String, result: String? = null): ExperienceRecord = ExperienceRecord.retrieve(
        id = expId,
        ownerId = ownerId,
        title = ExperienceTitle("결제 시스템 개편"),
        type = ExperienceType.PROJECT,
        body = ExperienceBody(body),
        detail = if (result == null) {
            ExperienceDetail.EMPTY
        } else {
            ExperienceDetail.of(situation = null, action = null, result = result, period = null, skillTags = emptyList())
        },
    )

    @Test
    fun 규모어가_있고_수치가_없으면_수치_보강을_유도한다() {
        // given — "대용량" 규모어가 있으나 구체 수치가 전혀 없다(결과 칸은 채워 다른 소견을 배제).
        val record = experience("대용량 트래픽을 처리하는 결제 시스템을 구축했습니다.", result = "장애 신고가 줄었습니다.")

        // when
        val review = service.review(record)

        // then — 모호 수치 보강 유도 1건, 근거(규모어)를 동반한다.
        val finding = review.findings.single { it.criterion == ExperienceReviewCriterion.VAGUE_METRIC }
        assertThat(finding.evidenceText).isEqualTo("대용량")
        assertThat(review.boostHintCount).isEqualTo(1)
    }

    @Test
    fun 규모어가_있어도_수치가_있으면_소견을_내지_않는다() {
        // given — "대용량" + 구체 수치(500). 이력서가 자동 객관화할 수 있으므로 경험 점검은 침묵한다.
        val record = experience("대용량 트래픽, 초당 500건을 처리했습니다.", result = "응답 지연을 줄였습니다.")

        // when
        val review = service.review(record)

        // then
        assertThat(review.findings.map { it.criterion }).doesNotContain(ExperienceReviewCriterion.VAGUE_METRIC)
    }

    @Test
    fun 성과_칸이_비어_있으면_성과_보강을_유도한다() {
        // given — 결과(result)가 비어 있다(본문은 충분히 길어 빈약 소견을 배제).
        val record = experience("결제 시스템을 새로 설계하고 캐시를 도입했습니다.", result = null)

        // when
        val review = service.review(record)

        // then
        val missingResult = review.findings.single { it.criterion == ExperienceReviewCriterion.MISSING_RESULT }
        assertThat(missingResult.field.name).isEqualTo("RESULT")
    }

    @Test
    fun 본문이_짧으면_보강을_유도한다() {
        // given — 본문이 임계 미만(트림 후 짧음). 결과는 채워 다른 소견을 배제.
        val record = experience("수정함", result = "성과 있었습니다.")

        // when
        val review = service.review(record)

        // then
        assertThat(review.findings.map { it.criterion }).contains(ExperienceReviewCriterion.THIN_BODY)
    }

    @Test
    fun 충분히_구체적인_경험은_소견이_없다() {
        // given — 규모어 없음, 본문 충분, 성과 기재. 보강할 게 없다(긍정 빈 상태).
        val record = experience("결제 흐름을 새로 설계해 사용자 불편을 줄였습니다.", result = "전환율이 올랐습니다.")

        // when
        val review = service.review(record)

        // then
        assertThat(review.findings).isEmpty()
        assertThat(review.boostHintCount).isEqualTo(0)
    }

    @Test
    fun 같은_입력은_결정적으로_같은_결과를_낸다() {
        // given
        val record = experience("대용량 트래픽을 처리했습니다.", result = null)

        // when
        val first = service.review(record)
        val second = service.review(record)

        // then
        assertThat(first.findings.map { it.criterion }).isEqualTo(second.findings.map { it.criterion })
    }

    @Test
    fun 타인_소유이거나_미존재면_404() {
        // given — findByIdAndOwnerId가 null.
        whenever(repository.findByIdAndOwnerId(any(), any())).thenReturn(null)

        // when and then
        assertThatThrownBy { service.review(ownerId, expId) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
