package watson.resumaker.template.presentation

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import watson.resumaker.template.domain.SectionCharacter

/**
 * 이력서 양식 생성 요청 DTO. Bean Validation은 필수값(이름·섹션 목록·각 섹션 이름·성격)만 검증한다(검증 가이드).
 * 중첩 SectionRequest는 @field:Valid로 함께 검증한다.
 */
data class CreateTemplateRequest(
    @field:NotBlank(message = "양식 이름을 적어 주세요. 예: 토스 백엔드 지원용")
    val name: String?,
    @field:NotEmpty(message = "담을 칸(섹션)을 하나 이상 추가해 주세요.")
    @field:Valid
    val sections: List<SectionRequest>? = null,
)

/**
 * 이력서 양식 수정 요청 DTO.
 */
data class UpdateTemplateRequest(
    @field:NotBlank(message = "양식 이름을 적어 주세요. 예: 토스 백엔드 지원용")
    val name: String?,
    @field:NotEmpty(message = "담을 칸(섹션)을 하나 이상 추가해 주세요.")
    @field:Valid
    val sections: List<SectionRequest>? = null,
)

/**
 * 섹션 정의 요청 DTO. 이름은 필수, 성격은 비널, 필수 여부는 토글.
 */
data class SectionRequest(
    @field:NotBlank(message = "섹션 이름을 적어 주세요. 예: 핵심 역량")
    val name: String?,
    @field:NotNull(message = "섹션 성격을 골라 주세요(요약형 또는 경력형).")
    val character: SectionCharacter?,
    val required: Boolean = false,
)

/**
 * 이력서 양식 응답 DTO.
 */
data class TemplateResponse(
    val id: String,
    val name: String,
    val sections: List<SectionResponse>,
)

/**
 * 섹션 정의 응답 DTO.
 */
data class SectionResponse(
    val name: String,
    val character: SectionCharacter,
    val required: Boolean,
)
