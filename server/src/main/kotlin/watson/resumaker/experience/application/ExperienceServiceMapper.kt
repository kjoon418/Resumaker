package watson.resumaker.experience.application

import org.springframework.stereotype.Component
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceReview
import watson.resumaker.experience.presentation.ExperienceResponse
import watson.resumaker.experience.presentation.ExperienceReviewFindingDto
import watson.resumaker.experience.presentation.ExperienceReviewResponse

/**
 * 서비스 계층의 Response DTO 변환을 담당한다(Service Mapper).
 */
@Component
class ExperienceServiceMapper {

    fun toResponse(record: ExperienceRecord, boostHintCount: Int): ExperienceResponse =
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
            boostHintCount = boostHintCount,
        )

    fun toReviewResponse(review: ExperienceReview): ExperienceReviewResponse =
        ExperienceReviewResponse(
            findings = review.findings.map {
                ExperienceReviewFindingDto(
                    criterion = it.criterion,
                    field = it.field,
                    message = it.message,
                    evidenceText = it.evidenceText,
                )
            },
        )
}
