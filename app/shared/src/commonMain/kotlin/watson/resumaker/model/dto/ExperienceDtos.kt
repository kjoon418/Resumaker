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
)
