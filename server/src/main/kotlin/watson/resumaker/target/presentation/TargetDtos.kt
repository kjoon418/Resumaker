package watson.resumaker.target.presentation

import jakarta.validation.constraints.NotBlank

/**
 * 목표 정보 생성 요청 DTO. Bean Validation은 필수값(채용 방향) null/blank만 검증한다(검증 가이드).
 * 회사명·직무명은 선택값.
 */
data class CreateTargetRequest(
    @field:NotBlank(message = "어떤 회사·직무를 겨냥하는지 알려주세요. 공고 내용을 붙여넣어도 좋아요.")
    val recruitDirection: String?,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

/**
 * 목표 정보 수정 요청 DTO.
 */
data class UpdateTargetRequest(
    @field:NotBlank(message = "어떤 회사·직무를 겨냥하는지 알려주세요. 공고 내용을 붙여넣어도 좋아요.")
    val recruitDirection: String?,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

/**
 * 목표 정보 응답 DTO.
 */
data class TargetResponse(
    val id: String,
    val recruitDirection: String,
    val companyName: String?,
    val jobTitle: String?,
)
