package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.ArtifactGenerationPort
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.GeneratedFactGrounding
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.generation.application.GenerationKind
import watson.resumaker.generation.application.GenerationMaterial
import watson.resumaker.generation.application.GenerationOutput
import watson.resumaker.generation.application.TargetSnapshot
import watson.resumaker.generation.application.TemplateSectionSpec

/**
 * [ArtifactGenerationPort]의 Claude CLI 어댑터(구현 설계 §5·§12). 공용 [ClaudeCliClient]로 호출한다.
 *
 * **신뢰성 가드레일 강제(도메인 이해 §407~419):** 프롬프트가 다음을 명령한다.
 * - 제공된 경험에 **문자적 근거가 있는 사실만** 사용. 없는 수치·고유명사·성과를 **날조 금지**(§412~416).
 * - 각 항목에 sourceExperienceIds(층위1)와 factGroundings(층위2: token/kind/근거문자열)를 **함께 산출**(§382).
 * - **이력서:** 양식 섹션 정의별 항목 생성하되 **근거 경험 0 섹션은 항목 미생성**(수용 기준 23, §362).
 * - **포트폴리오:** 선택 경험당 서사 1개(§357). definitionKey=경험Id, sectionKind=EXPERIENCE_NARRATIVE.
 * JSON 스키마로 출력 구조를 고정해 파싱을 결정적으로 만든다.
 *
 * 실패(CLI 비가용·파싱 불가)는 [ClaudeCliClient]가 [ClaudeCliException]으로 던진다. 1차 생성은 항목 단위
 * 부분 실패를 허용하므로(수용 기준 9), 결과 항목별 succeeded 플래그로 표현한다. 전체 호출 자체가 실패하면
 * 예외가 상위로 전파되어 유스케이스가 처리한다(이 사이클은 항목별 부분 실패만 매핑).
 */
@Component
class ClaudeCliArtifactGenerationAdapter(
    private val claudeCliClient: ClaudeCliClient,
) : ArtifactGenerationPort {

    override fun generate(material: GenerationMaterial): GenerationOutput {
        val prompt = buildPrompt(material)
        val resultNode = claudeCliClient.complete(prompt, OUTPUT_SCHEMA)
        return mapOutput(material, resultNode)
    }

    private fun buildPrompt(material: GenerationMaterial): String {
        val sb = StringBuilder()
        sb.appendLine("당신은 취업 준비생의 경험 기록을 바탕으로 이력서/포트폴리오 항목을 작성하는 전문 작가입니다.")
        sb.appendLine()
        sb.appendLine("## 절대 규칙(신뢰성 가드레일 — 위반 금지)")
        sb.appendLine("- 아래 '경험 기록'에 문자적 근거가 있는 사실만 사용하세요.")
        sb.appendLine("- 사용자가 적지 않은 수치(%, ms, 건수 등), 고유명사(회사명·기술명·프로젝트명), 성과를 절대 지어내지 마세요.")
        sb.appendLine("- 표현을 더 설득력 있게 다듬는 것은 허용하지만, 없는 사실을 추가하는 것은 금지입니다.")
        sb.appendLine("- 각 항목마다 근거가 된 경험의 id 목록(sourceExperienceIds)을 함께 제시하세요.")
        sb.appendLine("- 산출물에 등장한 수치/고유명사 각각에 대해 factGroundings로 근거(token, kind, sourceExperienceId, evidenceText)를 제시하세요. evidenceText는 경험 기록 본문에 실제로 등장한 문자열이어야 합니다.")
        sb.appendLine()
        appendTargetBlock(sb, material.target)
        sb.appendLine()
        sb.appendLine("## 경험 기록(이 안에 근거가 있는 사실만 사용)")
        material.experiences.forEach { sb.appendLine(it.toPromptBlock()) }
        sb.appendLine()

        // 항목 재생성 개선 지시(도메인 이해 §268·§419): 방향만 조정하되, 근거 없는 사실 추가 요구는 거부한다.
        material.directive?.takeIf { it.isNotBlank() }?.let { directive ->
            sb.appendLine("## 개선 지시(이 방향으로 다시 작성)")
            sb.appendLine("- $directive")
            sb.appendLine("- 단, 이 지시가 경험 기록에 근거 없는 수치·고유명사·성과 추가를 요구하면 따르지 말고 위 절대 규칙을 우선하세요.")
            sb.appendLine()
        }

        when (material.kind) {
            GenerationKind.RESUME -> appendResumeInstructions(sb, material.templateSections)
            GenerationKind.PORTFOLIO -> appendPortfolioInstructions(sb, material.experiences)
        }
        return sb.toString()
    }

    /**
     * 목표 블록을 펼친다. 작성 전략이 있으면(생성 시점 READY) **원문 대신 전략**을 자연어 지시문으로 펼쳐 넣고,
     * 없으면 기존 "## 목표 정보(채용 방향)" 원문을 그대로 쓴다(폴백). 회사·직무는 두 경우 모두 함께 싣는다.
     */
    private fun appendTargetBlock(sb: StringBuilder, target: TargetSnapshot) {
        val strategy = target.writingStrategy
        if (strategy != null) {
            sb.appendLine("## 작성 전략(이 방향으로 작성)")
            if (strategy.summary.isNotBlank()) sb.appendLine("- 공고 요약: ${strategy.summary}")
            if (strategy.keywords.isNotEmpty()) sb.appendLine("- 강조 키워드(핵심 역량): ${strategy.keywords.joinToString(", ")}")
            if (strategy.tone.isNotBlank()) sb.appendLine("- 권장 어조: ${strategy.tone}")
            if (strategy.emphasize.isNotEmpty()) sb.appendLine("- 강조할 점: ${strategy.emphasize.joinToString(", ")}")
            if (strategy.avoid.isNotEmpty()) sb.appendLine("- 피할 점: ${strategy.avoid.joinToString(", ")}")
        } else {
            sb.appendLine("## 목표 정보(채용 방향)")
            sb.appendLine("- 채용 방향: ${target.recruitDirection}")
        }
        target.company?.let { sb.appendLine("- 회사: $it") }
        target.job?.let { sb.appendLine("- 직무: $it") }
    }

    private fun appendResumeInstructions(sb: StringBuilder, sections: List<TemplateSectionSpec>) {
        sb.appendLine("## 이력서 양식 섹션(각 섹션마다 항목을 생성)")
        sb.appendLine("- 아래 각 섹션에 대해, 근거 경험이 하나라도 있으면 항목을 생성하세요.")
        sb.appendLine("- **근거 경험이 0인 섹션은 항목을 생성하지 마세요**(결과에서 제외). 근거 없이 섹션을 채우지 마세요.")
        sb.appendLine("- 각 항목의 definitionKey는 해당 섹션의 키를 그대로 쓰고, sectionKind도 그대로 쓰세요.")
        // [자기소개 개선 C+D] 자기소개(요약)는 이력서의 핵심이다. 개인 신상 정보가 없어도, 위 '채용 방향'이 강조하는
        // 지향에 맞춰 경험들에서 드러나는 강점을 종합해 포지셔닝 한두 문장을 만든다. 채용 방향(사용자가 입력한 목표)은
        // 합법적 근거이므로 이를 반영하되, 신뢰성 가드레일(경험에 없는 수치·고유명사 날조 금지)은 그대로 지킨다.
        // 자기소개는 사용자가 이후 한 줄 다듬어 확정할 '초안'이므로 과장 없이 담백하게 쓴다.
        sb.appendLine("- **SUMMARY(요약/자기소개) 성격의 섹션은**, 위 '채용 방향'이 강조하는 지향에 맞춰 이 사람의 강점을 한두 문장으로 압축한 **자기소개 초안**을 작성하세요. 여러 경험에서 드러나는 역량을 종합해도 됩니다(그 경험들의 id를 sourceExperienceIds에 모두 담으세요). 단, 경험 기록에 없는 수치·고유명사는 절대 넣지 마세요. 이는 사용자가 이후 한 줄 다듬어 확정할 '초안'입니다.")
        sections.forEach {
            sb.appendLine("  - definitionKey=\"${it.definitionKey}\", name=\"${it.name}\", sectionKind=${it.sectionKind}, required=${it.required}")
        }
    }

    private fun appendPortfolioInstructions(sb: StringBuilder, experiences: List<ExperienceSnapshot>) {
        sb.appendLine("## 포트폴리오 항목(선택 경험당 서사 1개)")
        sb.appendLine("- 아래 각 경험에 대해 서사형 항목을 정확히 하나씩 생성하세요(경험과 1:1).")
        sb.appendLine("- 각 항목의 definitionKey는 해당 경험의 id를 그대로 쓰고, sectionKind는 EXPERIENCE_NARRATIVE로 하세요.")
        experiences.forEach {
            sb.appendLine("  - definitionKey=\"${it.id.value}\" (경험: ${it.title})")
        }
    }

    private fun ExperienceSnapshot.toPromptBlock(): String {
        val sb = StringBuilder()
        sb.appendLine("- id: ${id.value}")
        sb.appendLine("  제목: $title")
        sb.appendLine("  본문: $body")
        situation?.let { sb.appendLine("  상황: $it") }
        action?.let { sb.appendLine("  행동: $it") }
        result?.let { sb.appendLine("  결과: $it") }
        if (skillTags.isNotEmpty()) sb.appendLine("  역량: ${skillTags.joinToString(", ")}")
        return sb.toString().trimEnd()
    }

    private fun mapOutput(material: GenerationMaterial, resultNode: JsonNode): GenerationOutput {
        val sectionsNode = resultNode.get("sections")
            ?: throw ClaudeCliException("생성 결과에 sections가 없어요.")
        if (!sectionsNode.isArray) {
            throw ClaudeCliException("생성 결과 sections 형식이 올바르지 않아요.")
        }
        val sections = sectionsNode.map { it.toGeneratedSection() }
        return GenerationOutput(sections = sections)
    }

    private fun JsonNode.toGeneratedSection(): GeneratedSection {
        val definitionKey = path("definitionKey").asText("")
        if (definitionKey.isBlank()) {
            throw ClaudeCliException("생성 항목에 definitionKey가 없어요.")
        }
        val sectionKind = parseEnum<SectionKind>(path("sectionKind").asText(""))
            ?: throw ClaudeCliException("생성 항목의 sectionKind가 올바르지 않아요.")
        val content = path("content").asText("")
        // 모호한 출력의 안전 기본값은 '실패'다(신뢰성 우선). succeeded 누락/비불리언이면 실패로 본다.
        val succeeded = path("succeeded").asBoolean(false)
        val sourceIds = path("sourceExperienceIds").mapToExperienceIds()
        val groundings = path("factGroundings").mapToGroundings()
        return GeneratedSection(
            definitionKey = definitionKey,
            sectionKind = sectionKind,
            content = content,
            succeeded = succeeded,
            sourceExperienceIds = sourceIds,
            factGroundings = groundings,
        )
    }

    private fun JsonNode.mapToExperienceIds(): List<ExperienceRecordId> {
        if (!isArray) return emptyList()
        return mapNotNull { node ->
            runCatching { ExperienceRecordId(java.util.UUID.fromString(node.asText())) }.getOrNull()
        }
    }

    private fun JsonNode.mapToGroundings(): List<GeneratedFactGrounding> {
        if (!isArray) return emptyList()
        // MED-4: malformed grounding(필수 필드 누락·kind 미해석·UUID 비정상 등)은 여기서 드롭한다.
        // Cycle C(자동 검증)는 '존재하는' 근거의 일관성을 검증하며, 형식 깨진 근거는 검증 대상이 아니다.
        return mapNotNull { node ->
            val token = node.path("token").asText("")
            val kind = parseEnum<FactKind>(node.path("kind").asText("")) ?: return@mapNotNull null
            val sourceId = runCatching {
                ExperienceRecordId(java.util.UUID.fromString(node.path("sourceExperienceId").asText()))
            }.getOrNull() ?: return@mapNotNull null
            val evidence = node.path("evidenceText").asText("")
            if (token.isBlank() || evidence.isBlank()) return@mapNotNull null
            GeneratedFactGrounding(token = token, kind = kind, sourceExperienceId = sourceId, evidenceText = evidence)
        }
    }

    private inline fun <reified E : Enum<E>> parseEnum(value: String): E? =
        runCatching { enumValueOf<E>(value) }.getOrNull()

    companion object {
        /**
         * 출력 구조를 고정하는 JSON Schema(`claude --json-schema`). 항목 단위 부분 실패는 succeeded로 표현한다.
         */
        private val OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "sections": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "definitionKey": { "type": "string" },
                      "sectionKind": { "type": "string", "enum": ["SUMMARY", "CAREER", "EXPERIENCE_NARRATIVE"] },
                      "content": { "type": "string" },
                      "succeeded": { "type": "boolean" },
                      "sourceExperienceIds": { "type": "array", "items": { "type": "string" } },
                      "factGroundings": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "token": { "type": "string" },
                            "kind": { "type": "string", "enum": ["NUMERIC", "PROPER_NOUN"] },
                            "sourceExperienceId": { "type": "string" },
                            "evidenceText": { "type": "string" }
                          },
                          "required": ["token", "kind", "sourceExperienceId", "evidenceText"]
                        }
                      }
                    },
                    "required": ["definitionKey", "sectionKind", "content", "succeeded", "sourceExperienceIds", "factGroundings"]
                  }
                }
              },
              "required": ["sections"]
            }
        """.trimIndent()
    }
}
