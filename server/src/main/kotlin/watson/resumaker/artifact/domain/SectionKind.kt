package watson.resumaker.artifact.domain

/**
 * 생성 항목의 종류(도메인 이해 §353 "생성 항목의 종류와 경험 카디널리티").
 * - 이력서: SUMMARY(요약형, 경험과 N:M), CAREER(경력형, 하나 이상 경험에 근거).
 * - 포트폴리오: EXPERIENCE_NARRATIVE(선택 경험과 1:1).
 *
 * enum 상수명은 클라이언트와 1:1로 직렬화되므로 그대로 유지한다.
 */
enum class SectionKind {
    SUMMARY,
    CAREER,
    EXPERIENCE_NARRATIVE,
}
