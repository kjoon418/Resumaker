package watson.resumaker.generation.application

import watson.resumaker.template.domain.SectionDefinition

/**
 * AI 생성 양식 포트(도메인 이해 §178~180·§446, 수용 기준 22). 사용자가 양식을 지정하지 않았을 때,
 * 경험 묶음·목표 정보를 근거로 이력서의 **섹션 구조(섹션 정의 목록)** 만 결정한다.
 *
 * **명명·해석기 재사용 불가 근거:** [watson.resumaker.template.application.ResumeTemplateInterpreter]는
 * "붙여넣은 텍스트 → 섹션 정의" 해석이고, 이 포트는 "입력 텍스트 없이 경험·목표 → 섹션 정의" 생성이라
 * 입력 계약이 다르다(전자는 pastedText, 후자는 경험·목표 스냅샷). 따라서 별도 포트로 둔다.
 *
 * **구조만, 사실 금지(§166):** 결과는 섹션 정의([SectionDefinition]: 이름·성격·필수여부)와 순서뿐이다.
 * 사실 내용(항목 본문)은 이후 [ArtifactGenerationPort] 생성 단계가 만든다. 이 포트는 영속하지 않는다 —
 * 결과는 산출물 양식 스냅샷으로 변환되어 [ArtifactGenerationService]가 기존 흐름에 투입한다.
 *
 * 트랜잭션 밖에서 호출되는 외부 LLM 어댑터다([ArtifactGenerationPort]와 같은 tx 경계 규율).
 */
interface ResumeTemplateGenerator {
    fun generate(material: ResumeTemplateGenerationInput): ResumeTemplateGeneration
}

/**
 * AI 양식 생성 입력(트랜잭션 안에서 적재·검증된 스냅샷). 외부 호출 전에 구성된다.
 *
 * @param experiences 경험 묶음의 내용 스냅샷(섹션 구조를 가늠하는 근거).
 * @param target      목표 정보(채용 방향 필수 — 목표에 맞춘 섹션 구성을 유도).
 */
data class ResumeTemplateGenerationInput(
    val experiences: List<ExperienceSnapshot>,
    val target: TargetSnapshot,
)

/**
 * AI 양식 생성 결과 sealed 타입.
 * - [Generated]: 섹션 정의 목록이 생성됐다(최소 1개).
 * - [Unavailable]: 생성 불가(LLM 미연결·실패·결과 파싱 불가·섹션 0개). 유스케이스가 기본 구조로 폴백한다.
 */
sealed interface ResumeTemplateGeneration {
    data class Generated(val sections: List<SectionDefinition>) : ResumeTemplateGeneration
    data object Unavailable : ResumeTemplateGeneration
}
