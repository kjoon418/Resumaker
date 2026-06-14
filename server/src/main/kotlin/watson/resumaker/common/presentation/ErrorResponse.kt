package watson.resumaker.common.presentation

/**
 * 공통 에러 응답 포맷(UX 에러 응답 가이드).
 * code: 클라이언트 분기용 식별자.
 * message: 사용자 언어로 작성한, 해결 방법을 포함한 안내.
 * field: 어느 입력값이 문제인지(필수값 누락 등). 없으면 null.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val field: String? = null,
)
