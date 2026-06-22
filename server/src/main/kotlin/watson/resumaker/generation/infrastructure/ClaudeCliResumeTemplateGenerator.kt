package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.ResumeTemplateGeneration
import watson.resumaker.generation.application.ResumeTemplateGenerationInput
import watson.resumaker.generation.application.ResumeTemplateGenerator
import watson.resumaker.generation.application.TargetSnapshot
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
        // [자기소개 개선 C+D] 이 서비스는 이름·연락처·학력 같은 개인 신상 정보를 받지 않는다. 그런 칸을 만들면
        // 채울 근거가 없어 빈 항목(생성 실패)으로 남는다. 그래서 신상 섹션은 금지하고, 경험·채용 방향으로 채울 수
        // 있는 자기소개(요약)·역량·경력 성격만 만들게 한다(빈 섹션 실패 제거).
        sb.appendLine("- **'인적사항·기본 정보·연락처·이름·학력' 같은 개인 신상 섹션은 절대 만들지 마세요.** 이 서비스는 그런 정보를 받지 않아 채울 수 없습니다(빈 칸이 됩니다).")
        sb.appendLine("- **맨 앞에 '자기소개(요약)' 섹션을 하나 두세요**(character=SUMMARY, required=true). 이 사람을 아래 채용 방향에 맞춰 한두 문장으로 포지셔닝하는 칸입니다.")
        sb.appendLine("- 모든 섹션은 경험·목표로 채울 수 있는 요약(SUMMARY)·경력(CAREER) 성격만 만드세요.")
        sb.appendLine()
        appendTargetBlock(sb, material.target)
        sb.appendLine()
        sb.appendLine("## 경험 기록(섹션 구조를 가늠하는 근거)")
        material.experiences.forEach { sb.appendLine(it.toPromptBlock()) }
        return sb.toString()
    }

    /**
     * 목표 블록을 펼친다. 작성 전략이 있으면(생성 시점 READY) 원문 대신 전략을 자연어 지시문으로 펼쳐 넣고,
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
