package watson.resumaker.experience.presentation

import org.springframework.stereotype.Component
import watson.resumaker.experience.application.CreateExperienceCommand
import watson.resumaker.experience.application.UpdateExperienceCommand
import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.Period
import watson.resumaker.experience.domain.SkillTag

/**
 * 컨트롤러 계층의 원시 값 -> VO 변환을 담당한다(아키텍처 가이드).
 */
@Component
class ExperienceMapper {

    fun toCreateCommand(request: CreateExperienceRequest): CreateExperienceCommand =
        CreateExperienceCommand(
            title = ExperienceTitle(request.title!!),
            type = request.type!!,
            body = ExperienceBody(request.body!!),
            detail = toDetail(request.detail),
        )

    fun toUpdateCommand(request: UpdateExperienceRequest): UpdateExperienceCommand =
        UpdateExperienceCommand(
            title = ExperienceTitle(request.title!!),
            type = request.type!!,
            body = ExperienceBody(request.body!!),
            detail = toDetail(request.detail),
        )

    private fun toDetail(request: ExperienceDetailRequest?): ExperienceDetail {
        if (request == null) {
            return ExperienceDetail.EMPTY
        }

        val period = toPeriod(request)
        val skillTags = request.skillTags
            .filter { it.isNotBlank() }
            .map { SkillTag(it) }
        return ExperienceDetail.of(
            situation = request.situation,
            action = request.action,
            result = request.result,
            period = period,
            skillTags = skillTags,
        )
    }

    private fun toPeriod(request: ExperienceDetailRequest): Period? {
        val start = request.periodStart
        val end = request.periodEnd
        return if (start != null && end != null) Period.of(start, end) else null
    }
}
