package watson.resumaker.generation.presentation

import org.springframework.stereotype.Component
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.GeneratePortfolioCommand
import watson.resumaker.generation.application.GenerateResumeCommand
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.template.domain.ResumeTemplateId
import java.util.UUID

/**
 * 컨트롤러 계층의 원시 값 → VO/Command 변환을 담당한다(구현 설계 §8 계층 협력).
 * Bean Validation을 통과한 뒤이므로 필수값(experienceIds·targetId·templateId)은 non-null로 단정한다.
 *
 * 양식/목표 로딩(소유 격리)은 Service의 책임이므로 여기서는 식별자 VO만 만든다(presentation은 도메인 적재를 모름).
 */
@Component
class GenerationMapper {

    fun toResumeCommand(request: ResumeGenerationRequest): GenerateResumeCommand =
        GenerateResumeCommand(
            experienceIds = request.experienceIds!!.map { ExperienceRecordId(UUID.fromString(it)) },
            targetId = TargetBriefId(UUID.fromString(request.targetId!!)),
            templateId = ResumeTemplateId(UUID.fromString(request.templateId!!)),
        )

    fun toPortfolioCommand(request: PortfolioGenerationRequest): GeneratePortfolioCommand =
        GeneratePortfolioCommand(
            experienceIds = request.experienceIds!!.map { ExperienceRecordId(UUID.fromString(it)) },
            targetId = TargetBriefId(UUID.fromString(request.targetId!!)),
        )
}
