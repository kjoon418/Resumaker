package watson.resumaker.quality.domain

/**
 * 개선 기준 카탈로그(품질 개선 기획 §1·§3.1). 서비스가 보유한 **고정 카탈로그**이며 사용자가 편집하지 않는다(MVP).
 *
 * 각 기준은 식별자(criterionId)·사람이 읽는 라벨(label)·범주(category)·**점검 가능성**([Checkability])을 메타로 가진다.
 * 진단(품질 점검)은 이 카탈로그에 비추어 산출물을 결정적 검사하고, 위반/약점을 [Finding]으로 낸다.
 *
 * 이 enum은 MVP 자동 개선 범위(이력서 본문 텍스트)에서 **결정적·반자동으로 점검 가능한 기준만** 담는다.
 * 범위 밖(R1 서식·AP14/AP15 외부 자산)은 후보를 만들지 않으므로(QC12) 별도 enum 상수를 두지 않는다.
 */
enum class QualityCriterion(
    val criterionId: String,
    val label: String,
    val category: String,
    val checkability: Checkability,
) {
    /** I1 강한 동사 — 책임 나열("담당했다") 대신 행동·성취 동사를 쓴다(반자동: 약한 동사 사전 매칭). */
    STRONG_VERB("I1", "약한 동사를 강한 행동·성취 동사로 바꾸면 더 좋아요", "임팩트·구체성", Checkability.SEMI_AUTO),

    /** I2 능동태 — 수동태("개선되었다") 대신 능동태로 주체를 드러낸다(반자동: 수동 표현 패턴 매칭). */
    ACTIVE_VOICE("I2", "수동태를 능동태로 바꾸면 주체가 더 잘 드러나요", "임팩트·구체성", Checkability.SEMI_AUTO),

    /** I4 객관적 수치화(as-is→to-be) — 모호 수치·규모어를 대상+실측값+단위로(반자동: 규모어 패턴, 근거 유무로 분기). */
    VAGUE_METRIC("I4", "모호한 수치·규모어를 구체적인 값으로 바꾸면 설득력이 높아져요", "임팩트·구체성", Checkability.SEMI_AUTO),

    /** C1 분량 적정 — 항목이 과도하게 장황하지 않다(자동: 길이 상한 대비). */
    LENGTH("C1", "항목이 너무 길어요. 핵심만 남겨 줄이면 좋아요", "간결성·가독성", Checkability.AUTO),

    /** C2 버즈워드 절제 — 막연한 버즈워드를 남발하지 않는다(반자동: 버즈워드 사전 매칭). */
    BUZZWORD("C2", "막연한 버즈워드가 보여요. 구체적인 표현으로 바꾸면 좋아요", "간결성·가독성", Checkability.SEMI_AUTO),

    /** C3 중복 제거 — 같은 표현·내용이 여러 항목에 반복되지 않는다(반자동: 항목 간 유사도). */
    DUPLICATION("C3", "두 항목의 내용이 겹쳐요. 한쪽으로 모으면 좋아요", "간결성·가독성", Checkability.SEMI_AUTO),

    /** St2 빈 항목 없음 — 생성 성공 항목의 내용이 비어있지 않다(자동). */
    EMPTY_SECTION("St2", "내용이 비어 있는 항목이 있어요", "형식 충족", Checkability.AUTO),
    ;

    /** 점검 가능성(기획 §1): 자동(결정적)·반자동(휴리스틱)·사용자검토. MVP 진단은 AUTO·SEMI_AUTO만 낸다. */
    enum class Checkability {
        AUTO,
        SEMI_AUTO,
        USER_REVIEW,
    }
}
