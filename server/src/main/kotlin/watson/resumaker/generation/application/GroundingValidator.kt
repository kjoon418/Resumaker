package watson.resumaker.generation.application

/**
 * 자동 검증 계약(구현 설계 §6, 도메인 이해 §421~429). **Cycle C 구현.**
 *
 * 산출물 항목 하나를, 그 항목의 **출처 경험 기록 본문**과 대조해 근거 없는 정량 수치·고유명사가 있는지
 * 결정적으로 판정한다. LLM 심사 절대 금지(§424). 또한 AI가 신고한 층위2 factGroundings를 신뢰하지 않고,
 * **산출물 텍스트(content)에서 직접** 수치·고유명사를 추출해 대조한다(독립 추출 — §425).
 *
 * 결정적 구현은 [DeterministicGroundingValidator]가 담당한다(@Primary). 자동 실패 판정 대상은
 * **정량 수치(NUMERIC)와 고유명사(PROPER_NOUN) 두 범주뿐**이며, '성과 주장' 같은 자유 서술은 제외한다(§427).
 */
interface GroundingValidator {

    /**
     * 한 생성 항목을 그 항목의 출처 경험 본문과 대조해 검증한다.
     *
     * @param section 검증 대상 생성 항목(content에서 토큰을 독립 추출한다).
     * @param sources 그 항목의 출처 경험 스냅샷 목록(본문 등 — 근거 대조 대상). JPA 엔티티가 아닌 스냅샷을
     *                받아 순수·결정적이며 트랜잭션 밖에서 안전하게 호출된다.
     * @return 통과/실패 + 검출된 근거 없는 토큰 목록.
     */
    fun validate(section: GeneratedSection, sources: List<ExperienceSnapshot>): SectionValidationResult
}

/**
 * 한 항목의 검증 결과. valid=false인 항목을 VALIDATION_FAILED로 두고 자동 1회 재생성한다(§429).
 *
 * @param definitionKey 검증한 항목의 키.
 * @param valid         근거 없는 수치·고유명사가 0건이면 true.
 * @param ungroundedTokens 출처 본문에 근거가 없어 검출된 토큰 목록(표시·디버깅용). valid=true면 비어 있다.
 */
data class SectionValidationResult(
    val definitionKey: String,
    val valid: Boolean,
    val ungroundedTokens: List<UngroundedToken> = emptyList(),
)

/** 근거 없이 검출된 한 토큰(어떤 토큰이 어떤 범주로 검출됐는지). */
data class UngroundedToken(
    val text: String,
    val kind: watson.resumaker.artifact.domain.FactKind,
)
