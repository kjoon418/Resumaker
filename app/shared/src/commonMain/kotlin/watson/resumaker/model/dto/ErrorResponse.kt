package watson.resumaker.model.dto

import kotlinx.serialization.Serializable

/**
 * 공통 에러 응답(서버 `common.presentation.ErrorResponse`와 1:1, UX 에러 가이드).
 * code: 클라이언트 분기용. message: 사용자 언어 안내. field: 문제된 입력값(없으면 null).
 * action: "사용자가 해야 할 일" 힌트(예: ADD_EXPERIENCE). 없으면 null.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val field: String? = null,
    val action: String? = null,
)
