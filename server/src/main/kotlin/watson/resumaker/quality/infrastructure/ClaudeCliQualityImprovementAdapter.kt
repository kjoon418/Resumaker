package watson.resumaker.quality.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.GeneratedFactGrounding
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.generation.application.TargetSnapshot
import watson.resumaker.generation.infrastructure.ClaudeCliClient
import watson.resumaker.generation.infrastructure.ClaudeCliException
import watson.resumaker.generation.infrastructure.GroundingPromptParts
import watson.resumaker.quality.application.QualityImprovementInput
import watson.resumaker.quality.application.QualityImprovementPort

/**
 * [QualityImprovementPort]의 Claude CLI 어댑터(품질 개선 기획 §3.8 (나) 별도 개선 패스). 공용 [ClaudeCliClient]로
 * 호출한다(.cmd 개행 truncation 회피 내장 — ProcessRunner 직접 호출 금지).
 *
 * **입력은 경험 묶음이 아니라 항목 원본 텍스트 + 작성 전략 + 적용할 개선 소견**이다(§219). 프롬프트의 신뢰성 절대
 * 규칙·factGroundings 산출 구조·출력 스키마는 1차 생성 어댑터와 [GroundingPromptParts]로 **공유**한다 — "다듬되 없는
 * 사실 금지, 근거 함께 산출"이 동일하게 필요하기 때문이다.
 *
 * 출력은 1차 생성과 같은 sections 배열(단일 항목)이라, 호출자가 기존 신뢰성 검증·보존 검증을 그대로 적용한다.
 * 실패(CLI 비가용·파싱 불가)는 [ClaudeCliClient]가 [ClaudeCliException]으로 던진다(워커가 FAILED로 매핑).
 */
@Component
class ClaudeCliQualityImprovementAdapter(
    private val claudeCliClient: ClaudeCliClient,
) : QualityImprovementPort {

    override fun improve(input: QualityImprovementInput): GeneratedSection? {
        val prompt = buildPrompt(input)
        val resultNode = claudeCliClient.complete(prompt, GroundingPromptParts.OUTPUT_SCHEMA)
        return mapOutput(input, resultNode)
    }

    private fun buildPrompt(input: QualityImprovementInput): String {
        val sb = StringBuilder()
        sb.appendLine("당신은 취업 준비생의 이력서 항목을 **사실은 그대로 두고 표현·구조만** 다듬는 전문 편집자입니다.")
        sb.appendLine()
        // 신뢰성 절대 규칙은 1차 생성과 공유한다(없는 사실 금지 + 근거 산출).
        GroundingPromptParts.appendAbsoluteRules(sb)
        sb.appendLine()

        appendTargetBlock(sb, input.target)
        sb.appendLine()

        sb.appendLine("## 경험 기록(이 안에 근거가 있는 사실만 사용)")
        input.experiences.forEach { sb.appendLine(it.toPromptBlock()) }
        sb.appendLine()

        sb.appendLine("## 다듬을 항목(원본 — 이 사실을 절대 바꾸지 마세요)")
        sb.appendLine(input.originalContent)
        sb.appendLine()

        sb.appendLine("## 개선 방향(아래 점을 개선하되, 사실은 불변)")
        input.criteria.forEach { sb.appendLine("- $it") }
        sb.appendLine("- 위 원본의 수치·고유명사·성과는 **그대로 보존**하세요(누락·변형 금지). 표현·구조·강조·간결만 다듬으세요.")
        sb.appendLine()

        sb.appendLine("## 출력")
        sb.appendLine("- sections 배열에 다듬은 항목을 **정확히 하나** 담으세요.")
        sb.appendLine("- definitionKey=\"${input.definitionKey}\", sectionKind=${input.sectionKind}로 하세요.")
        sb.appendLine("- 항목의 sourceExperienceIds에 위 경험 id를 담고, factGroundings로 등장한 수치·고유명사의 근거를 제시하세요.")
        sb.appendLine("- 안전하게 다듬을 수 없으면 succeeded=false로 두세요.")
        return sb.toString()
    }

    /** 목표 블록(1차 생성 어댑터와 동일 — 작성 전략이 있으면 전략, 없으면 채용 방향 원문). */
    private fun appendTargetBlock(sb: StringBuilder, target: TargetSnapshot) {
        val strategy = target.writingStrategy
        if (strategy != null) {
            sb.appendLine("## 작성 전략(이 방향으로 다듬기)")
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
        sb.appendLine("- id: ${id.value}")
        sb.appendLine("  제목: $title")
        sb.appendLine("  본문: $body")
        situation?.let { sb.appendLine("  상황: $it") }
        action?.let { sb.appendLine("  행동: $it") }
        result?.let { sb.appendLine("  결과: $it") }
        if (skillTags.isNotEmpty()) sb.appendLine("  역량: ${skillTags.joinToString(", ")}")
        return sb.toString().trimEnd()
    }

    private fun mapOutput(input: QualityImprovementInput, resultNode: JsonNode): GeneratedSection? {
        val sectionsNode = resultNode.get("sections")
            ?: throw ClaudeCliException("개선 결과에 sections가 없어요.")
        if (!sectionsNode.isArray) {
            throw ClaudeCliException("개선 결과 sections 형식이 올바르지 않아요.")
        }
        // 처치는 항목 1개를 다룬다. 어댑터가 키 일치 항목을 돌려주면 그것을, 없으면 첫 항목을 매핑한다(검증이 키를 강제).
        val node = sectionsNode.firstOrNull { it.path("definitionKey").asText("") == input.definitionKey }
            ?: sectionsNode.firstOrNull()
            ?: return null
        return node.toGeneratedSection()
    }

    private fun JsonNode.toGeneratedSection(): GeneratedSection {
        val definitionKey = path("definitionKey").asText("")
        if (definitionKey.isBlank()) {
            throw ClaudeCliException("개선 항목에 definitionKey가 없어요.")
        }
        val sectionKind = parseEnum<watson.resumaker.artifact.domain.SectionKind>(path("sectionKind").asText(""))
            ?: throw ClaudeCliException("개선 항목의 sectionKind가 올바르지 않아요.")
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
}
