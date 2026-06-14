package watson.resumaker.target.application

import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.JobTitle
import watson.resumaker.target.domain.RecruitDirection

/**
 * 목표 정보 생성 입력 DTO. 원시 값 대신 VO를 받는다(검증 가이드).
 */
data class CreateTargetCommand(
    val recruitDirection: RecruitDirection,
    val company: CompanyName?,
    val job: JobTitle?,
)

/**
 * 목표 정보 수정 입력 DTO.
 */
data class UpdateTargetCommand(
    val recruitDirection: RecruitDirection,
    val company: CompanyName?,
    val job: JobTitle?,
)
