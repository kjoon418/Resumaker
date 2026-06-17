package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.ResumeTemplateGeneration
import watson.resumaker.generation.application.ResumeTemplateGenerationInput
import watson.resumaker.generation.application.ResumeTemplateGenerator
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition

/**
 * AI 생성 양식의 Claude CLI 어댑터(도메인 이해 §178~180, 수용 기준 22). 공용 [ClaudeCliClient]로
 * 경험 묶음·목표를 근거로 이력서 섹션 정의 목록을 생성한다. @Primary로 CLI 어댑터를 우선 사용한다.
 *
 * **구조만, 사실 금지(§166):** 프롬프트가 섹션 구조(name·character·required)만 만들고 사실 내용은 만들지
 * 말도록 명령한다. 항목 본문은 이후 [ClaudeCliArtifactGenerationAdapter] 생성 단계가 만든다.
 *
 * **graceful 폴백(도메인: 차단 않고 폴백, §186):** CLI 실패·비가용([ClaudeCliException])·결과 파싱 불가·
 * 섹션 0개 → 예외를 던지지 않고 [ResumeTemplateGeneration.Unavailable]을 반환한다. 유스케이스는 이를 받아
 * 기본 구조(프리셋류)로 막다른 길 없이 생성을 진행한다.
 *
 * 이 포트는 영속하지 않는다 — 결과는 산출물 양식 스냅샷으로 변환되어 생성 흐름에 투입된다.
 */
@Primary
@Component
class ClaudeCliResumeTemplateGenerator(
    private val claudeCliClient: ClaudeCliClient,
) : ResumeTemplateGenerator {

    override fun generate(material: ResumeTemplateGenerationInput): ResumeTemplateGeneration {
        val resultNode = try {
            claudeCliClient.complete(buildPrompt(material), OUTPUT_SCHEMA)
        } catch (e: ClaudeCliException) {
            // CLI 실패/비가용 → 차단하지 않고 폴백(유스케이스가 기본 구조로 진행).
            return ResumeTemplateGeneration.Unavailable
        }

        val sections = parseSections(resultNode) ?: return ResumeTemplateGeneration.Unavailable
        if (sections.isEmpty()) {
            return ResumeTemplateGeneration.Unavailable
        }
        return ResumeTemplateGeneration.Generated(sections)
    }

    private fun parseSections(resultNode: JsonNode): List<SectionDefinition>? {
        val sectionsNode = resultNode.get("sections")
        if (sectionsNode == null || !sectionsNode.isArray) {
            return null
        }
        // per-section 관용: 한 섹션이 깨져도 나머지 유효 섹션은 살린다(이름 길이 초과 등).
        // 전체 runCatching이면 한 섹션 오류가 전체를 Unavailable로 추락시키는 문제를 방지한다(LOW-1).
        return sectionsNode.mapNotNull { node ->
            runCatching {
                val name = node.path("name").asText("")
                if (name.isBlank()) return@runCatching null
                val character = parseCharacter(node.path("character").asText("")) ?: return@runCatching null
                val required = node.path("required").asBoolean(false)
                SectionDefinition.of(name, character, required)
            }.getOrNull()
        }
    }

    private fun parseCharacter(value: String): SectionCharacter? =
        runCatching { SectionCharacter.valueOf(value) }.getOrNull()

    private fun buildPrompt(material: ResumeTemplateGenerationInput): String {
        val sb = StringBuilder()
        sb.appendLine("당신은 취업 준비생의 경험과 목표에 맞춰 이력서의 섹션 구조(양식)를 설계하는 전문가입니다.")
        sb.appendLine()
        sb.appendLine("## 할 일")
        sb.appendLine("- 아래 경험 묶음과 목표 정보를 보고, 이 사람에게 어울리는 이력서 섹션 구조를 정하세요.")
        sb.appendLine("- 각 섹션은 name(섹션 이름), character(SUMMARY=요약형 또는 CAREER=경력형), required(필수 여부)를 가집니다.")
        sb.appendLine("- **구조(어떤 칸이 있는지)만** 만들고, 사실 내용(항목 본문)은 절대 만들지 마세요.")
        sb.appendLine("- 경험에 근거가 없는 섹션을 억지로 넣지 말고, 경험·목표에 맞는 섹션만 순서대로 제시하세요.")
        sb.appendLine()
        sb.appendLine("## 목표 정보(채용 방향)")
        sb.appendLine("- 채용 방향: ${material.target.recruitDirection}")
        material.target.company?.let { sb.appendLine("- 회사: $it") }
        material.target.job?.let { sb.appendLine("- 직무: $it") }
        sb.appendLine()
        sb.appendLine("## 경험 기록(섹션 구조를 가늠하는 근거)")
        material.experiences.forEach { sb.appendLine(it.toPromptBlock()) }
        return sb.toString()
    }

    private fun ExperienceSnapshot.toPromptBlock(): String {
        val sb = StringBuilder()
        sb.appendLine("- 제목: $title")
        sb.appendLine("  본문: $body")
        situation?.let { sb.appendLine("  상황: $it") }
        action?.let { sb.appendLine("  행동: $it") }
        result?.let { sb.appendLine("  결과: $it") }
        if (skillTags.isNotEmpty()) sb.appendLine("  역량: ${skillTags.joinToString(", ")}")
        return sb.toString().trimEnd()
    }

    companion object {
        private val OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "sections": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "character": { "type": "string", "enum": ["SUMMARY", "CAREER"] },
                      "required": { "type": "boolean" }
                    },
                    "required": ["name", "character", "required"]
                  }
                }
              },
              "required": ["sections"]
            }
        """.trimIndent()
    }
}
