package watson.resumaker.quality.application

import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.generation.application.TargetSnapshot

/**
 * 품질 개선 처치 포트(품질 개선 기획 §3.8 (나) 별도 개선 패스). 1차 생성 포트와 분리한다 — 입력이 *경험 묶음이
 * 아니라* **개선 대상 항목의 원본 텍스트 + 작성 전략 + 적용할 개선 소견(어떤 기준을)** 이고, 출력은 다듬어진 후보
 * 텍스트 + (검증용) 사실 토큰(factGroundings)이기 때문이다.
 *
 * 트랜잭션 밖에서 호출되는 외부 LLM 어댑터다(긴 트랜잭션 금지). 신뢰성 절대 규칙·근거 산출 구조는 1차 생성 어댑터와
 * 공유한다([watson.resumaker.generation.infrastructure.GroundingPromptParts]) — 다듬기도 LLM이 수행하므로 사실 날조
 * 위험이 동일.
 */
interface QualityImprovementPort {
    /**
     * 한 항목을 개선 소견 방향으로 다듬은 후보를 만든다. 출력은 1차 생성과 같은 [GeneratedSection] 형태라 기존
     * 신뢰성 검증([watson.resumaker.generation.application.GroundingValidator])을 그대로 적용할 수 있다.
     *
     * @return 다듬어진 후보 항목. 포트가 항목을 못 돌려주면(생성 실패) succeeded=false이거나 빈 결과일 수 있다.
     */
    fun improve(input: QualityImprovementInput): GeneratedSection?
}

/**
 * 처치 입력. 한 항목을 어떤 기준으로 다듬을지 좁힌 재료.
 *
 * @param definitionKey 다듬을 항목의 섹션 정의 키(출력도 같은 키여야 한다 — 매칭).
 * @param sectionKind   항목 종류(출력 스키마 enum과 정합).
 * @param originalContent 원본 항목 텍스트(이 사실을 불변으로 두고 표현만 다듬는다).
 * @param criteria      적용할 개선 기준(라벨 — 프롬프트에 "이 점을 개선" 지시로 펼친다).
 * @param target        작성 전략·채용 방향(다듬기 방향 정렬용 — 1차 생성과 동일 블록).
 * @param experiences   항목 출처 경험 스냅샷(근거 산출·검증 corpus용).
 * @param sourceExperienceIds 항목 출처 경험 식별자(출력 sourceExperienceIds로 보존).
 */
data class QualityImprovementInput(
    val definitionKey: String,
    val sectionKind: watson.resumaker.artifact.domain.SectionKind,
    val originalContent: String,
    val criteria: List<String>,
    val target: TargetSnapshot,
    val experiences: List<ExperienceSnapshot>,
    val sourceExperienceIds: List<watson.resumaker.experience.domain.ExperienceRecordId>,
)
