package watson.resumaker.template.presentation

import org.springframework.stereotype.Component
import watson.resumaker.template.application.CreateTemplateCommand
import watson.resumaker.template.application.UpdateTemplateCommand
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.domain.TemplateName

/**
 * 컨트롤러 계층의 원시 값 -> VO/Command 변환을 담당한다(아키텍처 가이드).
 * Bean Validation을 통과한 뒤이므로 필수값(name·sections·각 섹션 name/character)은 non-null로 단정한다.
 */
@Component
class TemplateMapper {

    fun toCreateCommand(request: CreateTemplateRequest): CreateTemplateCommand =
        CreateTemplateCommand(
            name = TemplateName(request.name!!),
            sections = request.sections!!.map { it.toDefinition() },
        )

    fun toUpdateCommand(request: UpdateTemplateRequest): UpdateTemplateCommand =
        UpdateTemplateCommand(
            name = TemplateName(request.name!!),
            sections = request.sections!!.map { it.toDefinition() },
        )

    private fun SectionRequest.toDefinition(): SectionDefinition =
        SectionDefinition.of(
            name = name!!,
            character = character!!,
            required = required,
        )
}
