package watson.resumaker.target.presentation

import org.springframework.stereotype.Component
import watson.resumaker.target.application.CreateTargetCommand
import watson.resumaker.target.application.UpdateTargetCommand
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.JobTitle
import watson.resumaker.target.domain.RecruitDirection

/**
 * 컨트롤러 계층의 원시 값 -> VO 변환을 담당한다(아키텍처 가이드).
 */
@Component
class TargetMapper {

    fun toCreateCommand(request: CreateTargetRequest): CreateTargetCommand =
        CreateTargetCommand(
            recruitDirection = RecruitDirection(request.recruitDirection!!),
            company = toCompany(request.companyName),
            job = toJob(request.jobTitle),
        )

    fun toUpdateCommand(request: UpdateTargetRequest): UpdateTargetCommand =
        UpdateTargetCommand(
            recruitDirection = RecruitDirection(request.recruitDirection!!),
            company = toCompany(request.companyName),
            job = toJob(request.jobTitle),
        )

    private fun toCompany(value: String?): CompanyName? =
        value?.takeIf { it.isNotBlank() }?.let { CompanyName(it) }

    private fun toJob(value: String?): JobTitle? =
        value?.takeIf { it.isNotBlank() }?.let { JobTitle(it) }
}
