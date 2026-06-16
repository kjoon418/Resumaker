package watson.resumaker.common.presentation

/**
 * 공통 에러 응답 포맷(UX 에러 응답 가이드).
 * code: 클라이언트 분기용 식별자.
 * message: 사용자 언어로 작성한, 해결 방법을 포함한 안내.
 * field: 어느 입력값이 문제인지(필수값 누락 등). 없으면 null.
 * action: 사용자가 취할 수 있는 해결 행동 힌트(예: 경험 추가 유도). 없으면 null.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val field: String? = null,
    val action: String? = null,
)
