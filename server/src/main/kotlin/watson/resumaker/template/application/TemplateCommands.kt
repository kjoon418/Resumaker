package watson.resumaker.template.application

import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.domain.TemplateName

/**
 * 이력서 양식 생성 입력 DTO. 원시 값 대신 VO/도메인 타입을 받는다(검증 가이드).
 */
data class CreateTemplateCommand(
    val name: TemplateName,
    val sections: List<SectionDefinition>,
)

/**
 * 이력서 양식 수정 입력 DTO.
 */
data class UpdateTemplateCommand(
    val name: TemplateName,
    val sections: List<SectionDefinition>,
)
