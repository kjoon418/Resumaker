package watson.resumaker.target.application

import org.springframework.stereotype.Component
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.presentation.TargetResponse

/**
 * 서비스 계층의 Response DTO 변환을 담당한다(Service Mapper).
 */
@Component
class TargetServiceMapper {

    fun toResponse(brief: TargetBrief): TargetResponse =
        TargetResponse(
            id = brief.id.value.toString(),
            recruitDirection = brief.recruitDirection.value,
            companyName = brief.company?.value,
            jobTitle = brief.job?.value,
        )

    fun toResponses(briefs: List<TargetBrief>): List<TargetResponse> =
        briefs.map { toResponse(it) }
}
