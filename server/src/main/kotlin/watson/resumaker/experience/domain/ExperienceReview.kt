package watson.resumaker.experience.domain

import java.util.UUID

/**
 * 경험 점검 기준(결정적). 이력서 품질 점검과 달리 **경험은 '사실의 원천'**이라 신뢰성 가드레일상 AI가 없는 수치·성과를
 * 지어낼 수 없다. 그래서 경험 점검은 자동 재작성이 아니라 **보강 유도(elicitation)** — 무엇을 더 적으면 좋은지 알려줄 뿐
 * 값을 짓지 않는다.
 */
enum class ExperienceReviewCriterion {
    /** 모호 수치·규모어가 있으나 구체 수치가 없음 → 수치 보강 유도(이력서 VAGUE_METRIC 자동 개선의 선행 조건). */
    VAGUE_METRIC,

    /** 성과(결과)가 비어 있음 → 무엇이 달라졌는지 보강 유도. */
    MISSING_RESULT,

    /** 본문이 너무 짧음 → 구체적 행동 보강 유도. */
    THIN_BODY,
}

/** 보강 유도가 가리키는 경험의 입력 필드(화면이 해당 칸을 강조한다). */
enum class ExperienceReviewField {
    BODY,
    RESULT,
}

/**
 * 경험 점검 소견 한 건(보강 유도). [message]는 친근한 한국어 안내, [evidenceText]는 근거(예: 규모어 "대용량").
 */
data class ExperienceReviewFinding(
    val criterion: ExperienceReviewCriterion,
    val field: ExperienceReviewField,
    val message: String,
    val evidenceText: String? = null,
)

/**
 * 한 경험의 점검 결과(무LLM·무차감·결정적 — 같은 입력이면 같은 결과). 소견이 없으면 깨끗(보강 불필요).
 */
data class ExperienceReview(
    val experienceId: UUID,
    val findings: List<ExperienceReviewFinding>,
) {
    /** 보강 추천 개수(목록 배지·힌트용). 0이면 깨끗. */
    val boostHintCount: Int get() = findings.size
}
