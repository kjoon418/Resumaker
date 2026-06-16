package watson.resumaker.template.application

import watson.resumaker.template.domain.SectionDefinition

/**
 * 회사 양식 텍스트 → 섹션 정의 목록 해석 포트(FU-C, 도메인 이해 §2.5 "회사 양식 붙여넣기").
 *
 * LLM이 연결되지 않은 MVP 단계에서는 [UnavailableResumeTemplateInterpreter]가 항상
 * [TemplateInterpretation.Unavailable]을 반환한다. 실제 LLM 어댑터 도입 시 이 인터페이스를
 * 구현한 새 @Component로 교체한다(개방-폐쇄 원칙).
 *
 * 이 포트는 영속하지 않는다 — 해석 결과는 후보이며, 확정은 프론트가 `POST /resume-templates`로 수행한다.
 */
interface ResumeTemplateInterpreter {
    fun interpret(pastedText: String): TemplateInterpretation
}

/**
 * 해석 결과 sealed 타입.
 * - [Interpreted]: 섹션 정의 목록이 추출됐다.
 * - [Unavailable]: 해석 불가(LLM 미연결 또는 텍스트 판독 불가).
 */
sealed interface TemplateInterpretation {
    data class Interpreted(val sections: List<SectionDefinition>) : TemplateInterpretation
    data object Unavailable : TemplateInterpretation
}
