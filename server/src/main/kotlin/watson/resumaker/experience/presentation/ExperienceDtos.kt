package watson.resumaker.experience.presentation

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import watson.resumaker.experience.domain.ExperienceReviewCriterion
import watson.resumaker.experience.domain.ExperienceReviewField
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
 * 경험 기록 응답 DTO. [boostHintCount]는 경험 점검(결정적·무LLM)이 찾은 보강 추천 개수로, 목록 배지·상세 힌트에 쓴다(0이면 깨끗).
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
    val boostHintCount: Int,
)

/**
 * 경험 점검 응답 DTO — 보강 유도 소견 목록(자동 재작성 없음, 무엇을 더 적을지 안내만).
 */
data class ExperienceReviewResponse(
    val findings: List<ExperienceReviewFindingDto>,
)

/**
 * 경험 점검 소견 한 건. [field]로 화면이 해당 입력 칸을 강조하고, [evidenceText]는 근거(예: 규모어 "대용량").
 */
data class ExperienceReviewFindingDto(
    val criterion: ExperienceReviewCriterion,
    val field: ExperienceReviewField,
    val message: String,
    val evidenceText: String? = null,
)
