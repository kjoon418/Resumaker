package watson.resumaker.experience.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.domain.ExperienceReview
import watson.resumaker.experience.domain.ExperienceReviewCriterion
import watson.resumaker.experience.domain.ExperienceReviewField
import watson.resumaker.experience.domain.ExperienceReviewFinding
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.application.FactTokenExtractor
import watson.resumaker.quality.infrastructure.QualityCriteriaDictionary

/**
 * 경험 점검(진단) 유스케이스 — 작성한 경험에서 보강 포인트를 결정적으로 찾는다.
 *
 * 경험은 '사실의 원천'이라 신뢰성 가드레일상 AI가 없는 수치·성과를 지어낼 수 없다. 그래서 점검은 자동 재작성이 아니라
 * **보강 유도(elicitation)** — 무엇을 더 적으면 좋은지 알려줄 뿐 값을 짓지 않는다. 무LLM·무차감·결정적이다.
 *
 * 이력서 품질 점검([watson.resumaker.quality.application.QualityReviewService])과 **동일한 결정적 검사기**
 * (규모어 사전 [QualityCriteriaDictionary]·수치 추출 [FactTokenExtractor])를 재사용해, 이력서가 자동 개선하지 못하는
 * '모호 수치'의 **선행 조건(경험에 수치 부재)** 을 같은 기준으로 짚어 루프를 닫는다.
 */
@Service
class ExperienceReviewService(
    private val repository: ExperienceRecordRepository,
    private val criteria: QualityCriteriaDictionary,
    private val factTokenExtractor: FactTokenExtractor,
) {

    /** 소유 격리 점검(컨트롤러용). 타인·미존재는 404. */
    @Transactional(readOnly = true)
    fun review(ownerId: UserId, id: ExperienceRecordId): ExperienceReview {
        val record = repository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 경험 기록을 찾을 수 없어요.")
        return review(record)
    }

    /** 순수·결정적 점검(목록/상세의 boostHintCount 계산과 공유). */
    fun review(record: ExperienceRecord): ExperienceReview {
        val findings = mutableListOf<ExperienceReviewFinding>()
        val corpus = corpusOf(record)

        // 1) 모호 수치(핵심): 규모어가 있는데 구체 수치가 전혀 없으면 수치 보강을 유도한다. 수치가 하나라도 있으면
        //    이력서가 자동으로 객관화할 수 있으므로 소견을 내지 않는다(이력서 점검의 hasNumericEvidence와 동형).
        val vagueTerm = criteria.findVagueMetric(corpus)
        if (vagueTerm != null && factTokenExtractor.extractNumericValues(corpus).isEmpty()) {
            // AI-12: 역할/규모 형용사("중요한 역할"·"복잡한")는 수치가 아니라 근거(행동·기술)를 적도록 안내한다.
            val message = if (criteria.isVagueRoleAdjective(vagueTerm)) {
                "‘$vagueTerm’ 같은 표현이 모호해요. 어떤 행동·기술로 그렇게 판단했는지 구체적으로 적으면 이력서에서 더 강하게 보여줄 수 있어요."
            } else {
                "‘$vagueTerm’ 같은 표현이 있어요. 구체적인 수치를 적으면 이력서에서 더 강하게 보여줄 수 있어요(예: ‘대용량’→‘초당 500건’)."
            }
            findings += ExperienceReviewFinding(
                criterion = ExperienceReviewCriterion.VAGUE_METRIC,
                field = ExperienceReviewField.BODY,
                message = message,
                evidenceText = vagueTerm,
            )
        }

        // 2) 성과(결과) 누락: 결과 칸이 비어 있으면 무엇이 달라졌는지 적도록 유도(STAR 완성도·강한 이력서의 핵심).
        if (record.detail.result.isNullOrBlank()) {
            findings += ExperienceReviewFinding(
                criterion = ExperienceReviewCriterion.MISSING_RESULT,
                field = ExperienceReviewField.RESULT,
                message = "이 경험으로 무엇이 달라졌는지(성과)를 적어보세요. 수치가 있으면 더 좋아요.",
            )
        }

        // 3) 본문 빈약: 본문이 너무 짧으면 구체적 행동을 한 줄 더 적도록 유도.
        if (record.body.value.trim().length < MIN_BODY_LENGTH) {
            findings += ExperienceReviewFinding(
                criterion = ExperienceReviewCriterion.THIN_BODY,
                field = ExperienceReviewField.BODY,
                message = "내용이 짧아요. 어떤 문제를 어떻게 해결했는지 한 줄 더 적어보세요.",
            )
        }

        return ExperienceReview(experienceId = record.id.value, findings = findings)
    }

    /** 이력서 품질 점검의 corpusOf와 동형: 제목+본문+STAR+역량 태그를 한 코퍼스로 합친다. */
    private fun corpusOf(record: ExperienceRecord): String = buildString {
        append(record.title.value).append('\n')
        append(record.body.value).append('\n')
        record.detail.situation?.let { append(it).append('\n') }
        record.detail.action?.let { append(it).append('\n') }
        record.detail.result?.let { append(it).append('\n') }
        if (record.detail.skillTags.isNotEmpty()) {
            append(record.detail.skillTags.joinToString(" ") { it.value })
        }
    }

    companion object {
        /** 본문 빈약 임계(트림 후 글자수). 한 줄 이상이지만 구체성이 부족한 길이를 보강 유도한다. */
        const val MIN_BODY_LENGTH = 20
    }
}
