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

/**
 * 프리셋 양식 응답 DTO(FU-B). [key]는 프리셋 식별자, [name]은 표시 이름, [sections]는 섹션 정의 목록.
 */
data class TemplatePresetResponse(
    val key: String,
    val name: String,
    val sections: List<SectionResponse>,
)

/**
 * 회사 양식 붙여넣기 해석 요청 DTO(FU-C). [text]는 사용자가 붙여넣은 원문.
 */
data class InterpretRequest(
    @field:NotBlank(message = "해석할 양식 텍스트를 붙여넣어 주세요.")
    val text: String?,
)

/**
 * 해석 결과 응답 DTO(FU-C).
 * - status = "interpreted": [sections]에 후보 섹션 목록이 담긴다(후보, 영속 전).
 * - status = "unavailable": 해석 불가. [sections]는 빈 목록. 프론트는 폴백 UI를 보여준다.
 */
data class InterpretResponse(
    val status: String,
    val sections: List<SectionResponse> = emptyList(),
) {
    companion object {
        const val STATUS_INTERPRETED = "interpreted"
        const val STATUS_UNAVAILABLE = "unavailable"
    }
}
