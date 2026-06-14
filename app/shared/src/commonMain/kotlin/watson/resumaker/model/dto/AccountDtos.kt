package watson.resumaker.model.dto

import kotlinx.serialization.Serializable

/**
 * 회원가입 요청/응답 DTO. 서버 `account.presentation.AccountDtos`와 1:1.
 */
@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val timeZone: String? = null,
)

@Serializable
data class SignUpResponse(
    val userId: String,
)
