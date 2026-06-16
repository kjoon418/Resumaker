package watson.resumaker.template.infrastructure

import org.springframework.stereotype.Component
import watson.resumaker.template.application.ResumeTemplateInterpreter
import watson.resumaker.template.application.TemplateInterpretation

/**
 * 기본 해석 어댑터(FU-C). LLM이 연결되지 않은 MVP 단계에서 항상 [TemplateInterpretation.Unavailable]을 반환한다.
 * 실제 LLM 어댑터 도입 시 이 @Component를 대체하거나 @Primary로 교체한다.
 */
@Component
class UnavailableResumeTemplateInterpreter : ResumeTemplateInterpreter {
    override fun interpret(pastedText: String): TemplateInterpretation = TemplateInterpretation.Unavailable
}
