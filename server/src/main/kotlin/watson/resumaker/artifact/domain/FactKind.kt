package watson.resumaker.artifact.domain

/**
 * 고정밀 사실 근거(층위 2)의 종류(도메인 이해 §381).
 * - NUMERIC: 정량 수치.
 * - PROPER_NOUN: 회사명·기술명·프로젝트명 등 고유명사.
 *
 * enum 상수명은 클라이언트와 1:1로 직렬화되므로 그대로 유지한다.
 */
enum class FactKind {
    NUMERIC,
    PROPER_NOUN,
}
