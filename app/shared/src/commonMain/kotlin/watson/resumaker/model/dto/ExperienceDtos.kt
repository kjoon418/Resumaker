package watson.resumaker.model.dto

import kotlinx.serialization.Serializable
import watson.resumaker.model.type.ExperienceType

/**
 * 경험 기록 DTO. 서버 `experience.presentation.ExperienceDtos`와 1:1.
 *
 * 날짜(periodStart/periodEnd)는 서버가 `LocalDate`를 ISO 문자열("yyyy-MM-dd")로 직렬화하므로
 * 클라에서는 String으로 다룬다(kotlinx-datetime 의존 회피, 정합 유지).
 */
@Serializable
data class ExperienceDetailRequest(
    val situation: String? = null,
    val action: String? = null,
    val result: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val skillTags: List<String> = emptyList(),
)

@Serializable
data class CreateExperienceRequest(
    val title: String,
    val type: ExperienceType,
    val body: String,
    val detail: ExperienceDetailRequest? = null,
)

/**
 * 수정 요청. 서버 `UpdateExperienceRequest`와 동일 필드(Create와 같은 구조).
 */
@Serializable
data class UpdateExperienceRequest(
    val title: String,
    val type: ExperienceType,
    val body: String,
    val detail: ExperienceDetailRequest? = null,
)

@Serializable
data class ExperienceResponse(
    val id: String,
    val title: String,
    val type: ExperienceType,
    val body: String,
    val situation: String? = null,
    val action: String? = null,
    val result: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val skillTags: List<String> = emptyList(),
    /** 경험 점검(결정적·무LLM)이 찾은 보강 추천 개수. 목록 배지·상세 힌트용(0이면 깨끗). */
    val boostHintCount: Int = 0,
)

/** 경험 점검 기준. 서버 `experience.domain.ExperienceReviewCriterion`과 1:1. */
enum class ExperienceReviewCriterion { VAGUE_METRIC, MISSING_RESULT, THIN_BODY }

/** 보강 유도가 가리키는 입력 칸. 서버 `experience.domain.ExperienceReviewField`와 1:1. */
enum class ExperienceReviewField { BODY, RESULT }

/**
 * 경험 점검 응답. 보강 유도 소견 목록(자동 재작성 없음 — 무엇을 더 적을지 안내만). 서버 `ExperienceReviewResponse`와 1:1.
 */
@Serializable
data class ExperienceReviewResponse(
    val findings: List<ExperienceReviewFindingDto> = emptyList(),
)

@Serializable
data class ExperienceReviewFindingDto(
    val criterion: ExperienceReviewCriterion,
    val field: ExperienceReviewField,
    val message: String,
    val evidenceText: String? = null,
)
