package watson.resumaker.template.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import watson.resumaker.generation.infrastructure.ClaudeCliClient
import watson.resumaker.generation.infrastructure.ClaudeCliException
import watson.resumaker.template.application.ResumeTemplateInterpreter
import watson.resumaker.template.application.TemplateInterpretation
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition

/**
 * 회사 양식 붙여넣기 해석의 Claude CLI 어댑터(FU-C, 사용자 결정: 생성+해석 둘 다 CLI).
 *
 * 공용 [ClaudeCliClient]로 붙여넣은 텍스트를 섹션 정의 목록으로 해석한다. @Primary로 두어 CLI 어댑터를 우선
 * 사용하고, [UnavailableResumeTemplateInterpreter]는 폴백/기본으로 공존한다(둘 다 빈).
 *
 * **graceful 폴백(도메인: 차단 않고 폴백):** CLI 실패·비가용([ClaudeCliException])·결과 파싱 불가·섹션 0개 →
 * 예외를 던지지 않고 [TemplateInterpretation.Unavailable]을 반환한다. 프론트는 폴백 UI(직접 입력)를 보여준다.
 *
 * 이 포트는 영속하지 않는다 — 해석 결과는 후보이며, 확정은 프론트가 POST /resume-templates로 수행한다.
 */
@Primary
@Component
class ClaudeCliResumeTemplateInterpreter(
    private val claudeCliClient: ClaudeCliClient,
) : ResumeTemplateInterpreter {

    override fun interpret(pastedText: String): TemplateInterpretation {
        val resultNode = try {
            claudeCliClient.complete(buildPrompt(pastedText), OUTPUT_SCHEMA)
        } catch (e: ClaudeCliException) {
            // CLI 실패/비가용 → 차단하지 않고 폴백.
            return TemplateInterpretation.Unavailable
        }

        val sections = parseSections(resultNode) ?: return TemplateInterpretation.Unavailable
        if (sections.isEmpty()) {
            return TemplateInterpretation.Unavailable
        }
        return TemplateInterpretation.Interpreted(sections)
    }

    private fun parseSections(resultNode: JsonNode): List<SectionDefinition>? {
        val sectionsNode = resultNode.get("sections")
        if (sectionsNode == null || !sectionsNode.isArray) {
            return null
        }
        return runCatching {
            sectionsNode.mapNotNull { node ->
                val name = node.path("name").asText("")
                if (name.isBlank()) return@mapNotNull null
                val character = parseCharacter(node.path("character").asText("")) ?: return@mapNotNull null
                val required = node.path("required").asBoolean(false)
                SectionDefinition.of(name, character, required)
            }
        }.getOrNull()
    }

    private fun parseCharacter(value: String): SectionCharacter? =
        runCatching { SectionCharacter.valueOf(value) }.getOrNull()

    private fun buildPrompt(pastedText: String): String =
        """
        다음은 사용자가 붙여넣은 회사 이력서 양식 텍스트입니다. 이 텍스트에서 이력서 섹션 정의 목록을 추출하세요.
        각 섹션은 name(섹션 이름), character(SUMMARY=요약형 또는 CAREER=경력형), required(필수 여부)를 가집니다.
        양식의 구조(어떤 칸이 있는지)만 추출하고, 사실 내용은 만들지 마세요.

        ## 붙여넣은 양식 텍스트
        $pastedText
        """.trimIndent()

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
