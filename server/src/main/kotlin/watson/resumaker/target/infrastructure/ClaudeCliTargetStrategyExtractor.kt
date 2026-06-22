package watson.resumaker.target.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import watson.resumaker.generation.infrastructure.ClaudeCliClient
import watson.resumaker.generation.infrastructure.ClaudeCliException
import watson.resumaker.target.application.StrategyExtraction
import watson.resumaker.target.application.TargetStrategyExtractor
import watson.resumaker.target.domain.WritingStrategy

/**
 * [TargetStrategyExtractor]의 Claude CLI 어댑터([ClaudeCliResumeTemplateInterpreter]와 동형). 공용
 * [ClaudeCliClient]로 채용 방향에서 작성 전략(하이브리드 구조)을 추출한다. @Primary로 CLI 어댑터를 우선 사용한다.
 *
 * **graceful 폴백(도메인: 차단 않고 폴백):** CLI 실패·비가용([ClaudeCliException])·결과 파싱 불가·핵심 요약 누락 →
 * 예외를 던지지 않고 [StrategyExtraction.Unavailable]을 반환한다(워커가 FAILED로 표시, 생성은 원문 폴백).
 *
 * **부분 손상 관용:** 키워드/강조/회피 배열이 비거나 누락돼도 관용적으로 빈 목록으로 채운다. 단 [WritingStrategy.summary]가
 * 비면(작성 관점의 공고 요약 — 전략의 핵심) 전략으로서 의미가 없으므로 Unavailable로 본다.
 *
 * 이 포트는 영속하지 않는다 — 결과는 워커가 JSON으로 직렬화해 목표에 조건부로 쓴다.
 */
@Primary
@Component
class ClaudeCliTargetStrategyExtractor(
    private val claudeCliClient: ClaudeCliClient,
) : TargetStrategyExtractor {

    override fun extract(recruitDirection: String, company: String?, job: String?): StrategyExtraction {
        val resultNode = try {
            claudeCliClient.complete(buildPrompt(recruitDirection, company, job), OUTPUT_SCHEMA)
        } catch (e: ClaudeCliException) {
            // CLI 실패/비가용 → 차단하지 않고 폴백.
            return StrategyExtraction.Unavailable
        }

        val strategy = parseStrategy(resultNode) ?: return StrategyExtraction.Unavailable
        return StrategyExtraction.Extracted(strategy)
    }

    private fun parseStrategy(resultNode: JsonNode): WritingStrategy? {
        if (!resultNode.isObject) return null
        val summary = resultNode.path("summary").asText("").trim()
        // summary가 비면 작성 관점 요약이 없는 것 — 전략으로서 의미 없음(부분 손상 중 유일한 hard-fail).
        if (summary.isBlank()) return null
        val tone = resultNode.path("tone").asText("").trim()
        return WritingStrategy(
            keywords = resultNode.path("keywords").toStringList(),
            tone = tone,
            emphasize = resultNode.path("emphasize").toStringList(),
            avoid = resultNode.path("avoid").toStringList(),
            summary = summary,
        )
    }

    /** 문자열 배열을 관용적으로 읽는다(배열 아님·null·빈 문자열은 드롭). 누락/손상은 빈 목록으로 채운다. */
    private fun JsonNode.toStringList(): List<String> {
        if (!isArray) return emptyList()
        return mapNotNull { node ->
            node.asText("").trim().takeIf { it.isNotBlank() }
        }
    }

    private fun buildPrompt(recruitDirection: String, company: String?, job: String?): String {
        val sb = StringBuilder()
        sb.appendLine("다음 채용 공고/방향에서 이력서·포트폴리오 작성 전략을 추출하라.")
        sb.appendLine("복리후생·접수방법·근무지 등 작성과 무관한 정보는 버려라.")
        sb.appendLine("- keywords: 강조할 핵심 역량(키워드 목록)")
        sb.appendLine("- tone: 권장 어조")
        sb.appendLine("- emphasize: 강조할 점")
        sb.appendLine("- avoid: 피할 점")
        sb.appendLine("- summary: 작성 관점의 압축 공고 요약")
        sb.appendLine()
        company?.let { sb.appendLine("## 회사: $it") }
        job?.let { sb.appendLine("## 직무: $it") }
        sb.appendLine()
        sb.appendLine("## 채용 공고/방향")
        sb.appendLine(recruitDirection)
        return sb.toString()
    }

    companion object {
        private val OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "keywords": { "type": "array", "items": { "type": "string" } },
                "tone": { "type": "string" },
                "emphasize": { "type": "array", "items": { "type": "string" } },
                "avoid": { "type": "array", "items": { "type": "string" } },
                "summary": { "type": "string" }
              },
              "required": ["keywords", "tone", "emphasize", "avoid", "summary"]
            }
        """.trimIndent()
    }
}
