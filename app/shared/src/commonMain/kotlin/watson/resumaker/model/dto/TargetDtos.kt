package watson.resumaker.model.dto

import kotlinx.serialization.Serializable

/**
 * 목표 정보 DTO. 서버 `target.presentation.TargetDtos`와 1:1.
 */
@Serializable
data class CreateTargetRequest(
    val recruitDirection: String,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

@Serializable
data class UpdateTargetRequest(
    val recruitDirection: String,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

@Serializable
data class TargetResponse(
    val id: String,
    val recruitDirection: String,
    val companyName: String? = null,
    val jobTitle: String? = null,
)
