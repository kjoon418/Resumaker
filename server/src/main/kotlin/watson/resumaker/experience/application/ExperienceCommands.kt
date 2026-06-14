package watson.resumaker.experience.application

import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.ExperienceType

/**
 * 경험 기록 생성 입력 DTO. 원시 값 대신 VO를 받는다(검증 가이드).
 */
data class CreateExperienceCommand(
    val title: ExperienceTitle,
    val type: ExperienceType,
    val body: ExperienceBody,
    val detail: ExperienceDetail,
)

/**
 * 경험 기록 수정(점진 보강) 입력 DTO.
 */
data class UpdateExperienceCommand(
    val title: ExperienceTitle,
    val type: ExperienceType,
    val body: ExperienceBody,
    val detail: ExperienceDetail,
)
