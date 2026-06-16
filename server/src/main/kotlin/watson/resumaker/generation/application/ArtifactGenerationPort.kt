package watson.resumaker.generation.application

import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.experience.domain.ExperienceRecordId

/**
 * AI 생성 포트(구현 설계 §5의 `ResumeGenerationPort`).
 *
 * **명명 근거:** 설계 §5는 이력서 흐름을 예로 `ResumeGenerationPort`라 명명했으나, 도메인 이해 §4(포트폴리오 생성)도
 * 동일한 "재료 → 생성 결과(근거 동반)" 계약을 따른다. 이력서·포트폴리오를 모두 포괄하도록 `ArtifactGenerationPort`로
 * 명명한다(둘은 [GenerationMaterial.kind]로 구분). 트랜잭션 밖에서 호출되는 외부 LLM 어댑터다(설계 §5 흐름 3).
 *
 * 항목 단위 성공/실패(부분 실패 허용 — 도메인 이해 §369, 수용 기준 9)를 [GenerationOutput]으로 돌려준다.
 */
interface ArtifactGenerationPort {
    fun generate(material: GenerationMaterial): GenerationOutput
}

/**
 * 생성 재료(트랜잭션 안에서 적재·검증된 스냅샷). 외부 호출 전에 구성되어 포트에 넘겨진다.
 *
 * @param kind        RESUME | PORTFOLIO(이력서/포트폴리오 — domain ArtifactKind와 1:1).
 * @param experiences 경험 묶음의 **내용 스냅샷**(식별자 + 본문 등). 근거 대조와 프롬프트 구성에 쓰인다.
 * @param target      목표 정보(채용 방향 필수).
 * @param templateSections 이력서면 양식 섹션 정의(순서·키 포함), 포트폴리오면 빈 목록.
 * @param selectedExperienceIds 포트폴리오면 선택 경험 식별자 목록(경험당 서사 1개), 이력서면 빈 목록.
 */
data class GenerationMaterial(
    val kind: GenerationKind,
    val experiences: List<ExperienceSnapshot>,
    val target: TargetSnapshot,
    val templateSections: List<TemplateSectionSpec>,
    val selectedExperienceIds: List<ExperienceRecordId>,
)

/** 생성 종류(domain ArtifactKind와 분리해 application 입력 계약으로 둔다). */
enum class GenerationKind {
    RESUME,
    PORTFOLIO,
}

/** 경험 기록의 내용 스냅샷(근거 대조·프롬프트용). 도메인 엔티티를 인프라/포트에 직접 노출하지 않는다. */
data class ExperienceSnapshot(
    val id: ExperienceRecordId,
    val title: String,
    val body: String,
    val situation: String?,
    val action: String?,
    val result: String?,
    val skillTags: List<String>,
)

/** 목표 정보 스냅샷(채용 방향 필수, 회사·직무 선택). */
data class TargetSnapshot(
    val recruitDirection: String,
    val company: String?,
    val job: String?,
)

/**
 * 양식 섹션 정의 사양(이력서 생성용). definitionKey는 버전 간 항목 대응 키이자 양식 스냅샷 키다.
 * 생성 어댑터는 이 키로 항목을 산출하고, 근거 경험 0 섹션은 항목을 만들지 않는다(수용 기준 23).
 */
data class TemplateSectionSpec(
    val definitionKey: String,
    val name: String,
    val sectionKind: SectionKind,
    val required: Boolean,
)

/**
 * 생성 결과(구현 설계 §5, 도메인 이해 §378~382). 항목별 내용 + 생성 근거(층위1·2) + 성공/실패 표시.
 */
data class GenerationOutput(
    val sections: List<GeneratedSection>,
)

/**
 * 생성된 한 항목.
 *
 * @param definitionKey 이력서=양식 섹션정의 키 / 포트폴리오=경험Id 문자열.
 * @param sectionKind   SUMMARY|CAREER(이력서) / EXPERIENCE_NARRATIVE(포트폴리오).
 * @param content       생성 본문(실패면 비어 있을 수 있음).
 * @param succeeded     항목 단위 성공/실패(부분 실패 허용 — 수용 기준 9). 실패면 GENERATION_FAILED로 매핑.
 * @param sourceExperienceIds 생성 근거 층위1(항목 출처 경험 목록).
 * @param factGroundings 생성 근거 층위2(수치/고유명사 1—1 근거).
 */
data class GeneratedSection(
    val definitionKey: String,
    val sectionKind: SectionKind,
    val content: String,
    val succeeded: Boolean,
    val sourceExperienceIds: List<ExperienceRecordId>,
    val factGroundings: List<GeneratedFactGrounding>,
)

/** 생성 근거 층위2의 한 줄(token/kind/근거 경험/근거 문자열). */
data class GeneratedFactGrounding(
    val token: String,
    val kind: FactKind,
    val sourceExperienceId: ExperienceRecordId,
    val evidenceText: String,
)
