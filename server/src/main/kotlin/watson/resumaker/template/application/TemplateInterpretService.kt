package watson.resumaker.template.application

import org.springframework.stereotype.Service
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.presentation.InterpretResponse
import watson.resumaker.template.presentation.SectionResponse

/**
 * 회사 양식 붙여넣기 해석 유스케이스(FU-C, 도메인 이해 §2.5).
 *
 * 이 서비스는 영속하지 않는다. 해석 결과는 후보이며, 사용자가 확정하면
 * 프론트가 `POST /resume-templates`로 생성한다(도메인 이해 §2.5 수용기준24).
 */
@Service
class TemplateInterpretService(
    private val interpreter: ResumeTemplateInterpreter,
) {

    fun interpret(pastedText: String): InterpretResponse =
        when (val result = interpreter.interpret(pastedText)) {
            is TemplateInterpretation.Interpreted ->
                InterpretResponse(
                    status = InterpretResponse.STATUS_INTERPRETED,
                    sections = result.sections.map { it.toResponse() },
                )
            TemplateInterpretation.Unavailable ->
                InterpretResponse(status = InterpretResponse.STATUS_UNAVAILABLE)
        }
}

private fun SectionDefinition.toResponse() = SectionResponse(
    name = name,
    character = character,
    required = required,
)
