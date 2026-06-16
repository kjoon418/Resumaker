package watson.resumaker.artifact.domain

/**
 * 생성 항목 상태(구현 설계 §3.6, 도메인 이해 §372).
 *
 * 상태 전이:
 *   GENERATING ──► GENERATED
 *              ├─► GENERATION_FAILED   (AI 호출 실패/타임아웃 → 재시도 대기)
 *              └─► VALIDATION_FAILED   (자동 검증 실패 → 자동 1회 재생성)
 *
 * *_FAILED 항목도 정식 버전에 포함되어 저장된다(부분 실패 버전 — 수용 기준 9).
 * 이번 사이클(Cycle A)은 도메인 골격·영속만 다루므로 전이 트리거(AI 호출·검증)는 상위 사이클이 담당한다.
 *
 * enum 상수명은 클라이언트와 1:1로 직렬화되므로 그대로 유지한다.
 */
enum class SectionStatus {
    GENERATING,
    GENERATED,
    GENERATION_FAILED,
    VALIDATION_FAILED,
}
