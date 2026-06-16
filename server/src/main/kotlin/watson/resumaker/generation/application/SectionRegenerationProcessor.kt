package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.SectionStatus

/**
 * 한 생성 항목의 **포트 생성 + 자동 신뢰성 검증 + 검증실패 자동 1회 재생성**을 캡슐화한 공유 협력자
 * (도메인 이해 §421~429·§3.6, 구현 설계 §180·§220·§244).
 *
 * 1차 생성([ArtifactGenerationService])과 항목 단위 재생성([SectionRegenerationService]) **둘 다** 같은
 * 회복 규칙(검증실패 → 자동 1회 재생성 → 통과 시 GENERATED, 재실패 시 VALIDATION_FAILED 보존)을 써야 하므로,
 * 그 한 칸 로직을 여기로 모아 **진실의 원천을 하나로** 둔다. 트랜잭션 밖에서 호출된다(포트 호출이 외부 LLM).
 *
 * 자동 재시도는 비용 가드레일을 차감하지 않는다(§397) — 차감 로직은 Cycle 6 seam이라 여기서 호출도 없다.
 */
@Component
class SectionRegenerationProcessor(
    private val generationPort: ArtifactGenerationPort,
    private val groundingValidator: GroundingValidator,
) {

    /**
     * 이미 포트가 산출한 한 항목을 검증하고, 검증실패면 [material]을 그 항목으로 좁혀 자동 1회 재생성·재검증한다.
     *
     * @param material 그 항목 하나만 산출하도록 좁혀진 재료(templateSections/selectedExperienceIds 한정).
     * @param section  포트 생성 결과 항목(1차 생성·재생성 공통).
     * @return 확정된 항목 + 영속 상태(GENERATED|GENERATION_FAILED|VALIDATION_FAILED).
     */
    fun resolve(material: GenerationMaterial, section: GeneratedSection): ResolvedSection {
        if (!section.succeeded) {
            // 생성 자체가 실패한 항목은 검증 대상이 아니다(§429는 '생성 항목'=성공분에 적용).
            return ResolvedSection(section, SectionStatus.GENERATION_FAILED)
        }
        val firstResult = groundingValidator.validate(section, material.experiences)
        if (firstResult.valid) {
            return ResolvedSection(section, SectionStatus.GENERATED)
        }
        // VALIDATION_FAILED → 자동 1회 재생성(해당 항목만). 차감 없음(§397).
        val regenerated = regenerateSingle(material, section)
        if (regenerated == null || !regenerated.succeeded) {
            // 재생성 호출이 항목을 못 돌려주거나 생성 실패면 검증실패 유지(사용자 안내·재시도 경로).
            return ResolvedSection(section, SectionStatus.VALIDATION_FAILED)
        }
        val secondResult = groundingValidator.validate(regenerated, material.experiences)
        return if (secondResult.valid) {
            ResolvedSection(regenerated, SectionStatus.GENERATED)
        } else {
            // 재생성도 재검증 실패 → 검증실패 유지(§429 부분 실패와 동일 회복). 재생성 본문을 보존한다.
            ResolvedSection(regenerated, SectionStatus.VALIDATION_FAILED)
        }
    }

    /**
     * 검증실패 항목 하나만 포트로 재생성한다. 기존 포트 계약을 재사용하되 [material]을 그 항목의 정의/경험으로 좁혀
     * 재호출한다(포트·어댑터·스키마 불변, 단일 책임 유지). 포트가 그 키 항목을 못 돌려주면 null(→ 검증실패 유지).
     */
    private fun regenerateSingle(material: GenerationMaterial, failed: GeneratedSection): GeneratedSection? {
        val narrowed = when (material.kind) {
            GenerationKind.RESUME -> material.copy(
                templateSections = material.templateSections.filter { it.definitionKey == failed.definitionKey },
            )
            GenerationKind.PORTFOLIO -> material.copy(
                selectedExperienceIds = failed.sourceExperienceIds,
            )
        }
        if (narrowed.kind == GenerationKind.RESUME && narrowed.templateSections.isEmpty()) {
            // 양식에 없는 키였다면 정합화가 어차피 드롭하므로 재생성 의미 없음.
            return null
        }
        val regenOutput = generationPort.generate(narrowed)
        return regenOutput.sections.firstOrNull { it.definitionKey == failed.definitionKey }
    }
}

/**
 * 자동 검증·재생성 흐름이 확정한 최종 항목 + 영속 상태(§3.6). reconcile·영속화·채택은 이 확정 상태를 그대로 쓴다.
 */
data class ResolvedSection(
    val section: GeneratedSection,
    val status: SectionStatus,
)
