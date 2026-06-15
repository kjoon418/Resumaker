package watson.resumaker.template.application

import org.springframework.stereotype.Component
import watson.resumaker.template.domain.ResumeTemplate
import watson.resumaker.template.presentation.SectionResponse
import watson.resumaker.template.presentation.TemplateResponse

/**
 * 서비스 계층의 Response DTO 변환을 담당한다(Service Mapper).
 */
@Component
class TemplateServiceMapper {

    fun toResponse(template: ResumeTemplate): TemplateResponse =
        TemplateResponse(
            id = template.id.value.toString(),
            name = template.name.value,
            sections = template.sections.map { section ->
                SectionResponse(
                    name = section.name,
                    character = section.character,
                    required = section.required,
                )
            },
        )

    fun toResponses(templates: List<ResumeTemplate>): List<TemplateResponse> =
        templates.map { toResponse(it) }
}
