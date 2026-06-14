package watson.resumaker.experience.presentation

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import watson.resumaker.experience.domain.ExperienceType
import java.time.LocalDate

/**
 * 선택 항목(STAR·기간·역량) 요청 DTO. 모두 선택값이므로 Bean Validation을 적용하지 않는다.
 */
data class ExperienceDetailRequest(
    val situation: String? = null,
    val action: String? = null,
    val result: String? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val skillTags: List<String> = emptyList(),
)

/**
 * 경험 기록 생성 요청 DTO. Bean Validation은 필수값 null/blank 여부만 검증한다(검증 가이드).
 */
data class CreateExperienceRequest(
    @field:NotBlank(message = "경험의 제목을 입력해 주세요.")
    val title: String?,
    @field:NotNull(message = "경험 유형을 선택해 주세요.")
    val type: ExperienceType?,
    @field:NotBlank(message = "이 경험에서 무슨 일을 했는지 한 줄이라도 적어 주세요.")
    val body: String?,
    val detail: ExperienceDetailRequest? = null,
)

/**
 * 경험 기록 수정 요청 DTO.
 */
data class UpdateExperienceRequest(
    @field:NotBlank(message = "경험의 제목을 입력해 주세요.")
    val title: String?,
    @field:NotNull(message = "경험 유형을 선택해 주세요.")
    val type: ExperienceType?,
    @field:NotBlank(message = "이 경험에서 무슨 일을 했는지 한 줄이라도 적어 주세요.")
    val body: String?,
    val detail: ExperienceDetailRequest? = null,
)

/**
 * 경험 기록 응답 DTO.
 */
data class ExperienceResponse(
    val id: String,
    val title: String,
    val type: ExperienceType,
    val body: String,
    val situation: String?,
    val action: String?,
    val result: String?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val skillTags: List<String>,
)
