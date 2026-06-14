package watson.resumaker.experience.application

import org.springframework.stereotype.Component
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.presentation.ExperienceResponse

/**
 * 서비스 계층의 Response DTO 변환을 담당한다(Service Mapper).
 */
@Component
class ExperienceServiceMapper {

    fun toResponse(record: ExperienceRecord): ExperienceResponse =
        ExperienceResponse(
            id = record.id.value.toString(),
            title = record.title.value,
            type = record.type,
            body = record.body.value,
            situation = record.detail.situation,
            action = record.detail.action,
            result = record.detail.result,
            periodStart = record.detail.period?.start,
            periodEnd = record.detail.period?.end,
            skillTags = record.detail.skillTags.map { it.value },
        )

    fun toResponses(records: List<ExperienceRecord>): List<ExperienceResponse> =
        records.map { toResponse(it) }
}
