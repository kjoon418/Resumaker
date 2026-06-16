package watson.resumaker.artifact.domain

/**
 * 산출물 종류(도메인 이해 "산출물 구조와 상태 모델").
 * - RESUME(이력서): 양식 스냅샷을 가진다. 항목은 SUMMARY|CAREER.
 * - PORTFOLIO(포트폴리오): 양식 없음(스냅샷 null). 항목은 EXPERIENCE_NARRATIVE(경험과 1:1).
 *
 * enum 상수명은 클라이언트와 1:1로 직렬화되므로 그대로 유지한다.
 */
enum class ArtifactKind {
    RESUME,
    PORTFOLIO,
}
