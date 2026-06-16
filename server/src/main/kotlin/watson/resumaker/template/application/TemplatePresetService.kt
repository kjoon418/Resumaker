package watson.resumaker.template.application

import org.springframework.stereotype.Service
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.presentation.SectionResponse
import watson.resumaker.template.presentation.TemplatePresetResponse

/**
 * 프리셋 양식 조회 유스케이스(FU-B). 프리셋은 사용자 소유가 아니므로 ownerId가 불필요하다.
 * 프리셋 목록 조회와 단건 조회를 제공한다.
 */
@Service
class TemplatePresetService(
    private val presetProvider: TemplatePresetProvider,
) {

    fun getAll(): List<TemplatePresetResponse> =
        presetProvider.getAll().map { preset ->
            TemplatePresetResponse(
                key = preset.key,
                name = preset.name,
                sections = preset.sections.map { it.toResponse() },
            )
        }
}

private fun SectionDefinition.toResponse() = SectionResponse(
    name = name,
    character = character,
    required = required,
)
