package watson.resumaker.target.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.generation.infrastructure.ClaudeCliClient
import watson.resumaker.generation.infrastructure.ClaudeCliProperties
import watson.resumaker.generation.infrastructure.ProcessResult
import watson.resumaker.generation.infrastructure.ProcessRunner
import watson.resumaker.target.application.StrategyExtraction
import java.time.Duration

/**
 * [ClaudeCliTargetStrategyExtractor] 단위 테스트. **실제 CLI 미호출** — fake [ProcessRunner].
 * 구조화 파싱 / 부분 손상 관용 / summary 누락·CLI 실패 → Unavailable 폴백, 프롬프트·스키마 단정.
 */
class ClaudeCliTargetStrategyExtractorTest {

    private val objectMapper = ObjectMapper()
    private val properties = ClaudeCliProperties()

    /** capturedStdin/스키마를 들여다보기 위한 캡처 러너. */
    private class CapturingRunner(
        private val exitCode: Int,
        private val resultJson: String?,
        private val rawStdout: String?,
    ) : ProcessRunner {
        var capturedStdin: String = ""
        var capturedCommand: List<String> = emptyList()
        private val mapper = ObjectMapper()

        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
            capturedStdin = stdin
            capturedCommand = command
            val stdout = rawStdout ?: mapper.writeValueAsString(
                mapOf(
                    "type" to "result",
                    "is_error" to false,
                    "result" to "",
                    "structured_output" to mapper.readTree(resultJson ?: "{}"),
                ),
            )
            return ProcessResult(exitCode, stdout, "")
        }
    }

    private fun extractor(
        exitCode: Int = 0,
        resultJson: String? = null,
        rawStdout: String? = null,
    ): Pair<ClaudeCliTargetStrategyExtractor, CapturingRunner> {
        val runner = CapturingRunner(exitCode, resultJson, rawStdout)
        return ClaudeCliTargetStrategyExtractor(ClaudeCliClient(runner, properties, objectMapper)) to runner
    }

    @Test
    fun 정상_결과는_작성_전략으로_파싱된다() {
        // given
        val resultJson = """
            {
              "keywords": ["대용량 트래픽", "Kotlin"],
              "tone": "담백하고 성과 중심",
              "emphasize": ["백엔드 설계 경험"],
              "avoid": ["과장된 표현"],
              "summary": "백엔드 신입 — 대용량 처리 역량 강조"
            }
        """.trimIndent()

        // when
        val result = extractor(resultJson = resultJson).first.extract("채용 공고 전문", "토스", "백엔드")

        // then
        assertThat(result).isInstanceOf(StrategyExtraction.Extracted::class.java)
        val strategy = (result as StrategyExtraction.Extracted).strategy
        assertThat(strategy.keywords).containsExactly("대용량 트래픽", "Kotlin")
        assertThat(strategy.tone).isEqualTo("담백하고 성과 중심")
        assertThat(strategy.emphasize).containsExactly("백엔드 설계 경험")
        assertThat(strategy.avoid).containsExactly("과장된 표현")
        assertThat(strategy.summary).isEqualTo("백엔드 신입 — 대용량 처리 역량 강조")
    }

    @Test
    fun 프롬프트는_무관한_정보_버리기_지시와_5필드_스키마를_담는다() {
        // given
        val (ext, runner) = extractor(resultJson = """{"summary":"x","keywords":[],"tone":"","emphasize":[],"avoid":[]}""")

        // when
        ext.extract("채용 공고 전문", "토스", "백엔드")

        // then — 프롬프트에 핵심 지시·필드 설명이 들어가고, 스키마(인자)에 5필드가 있다.
        assertThat(runner.capturedStdin).contains("복리후생·접수방법·근무지 등 작성과 무관한 정보는 버려라")
        assertThat(runner.capturedStdin).contains("채용 공고 전문")
        val schemaArg = runner.capturedCommand.joinToString(" ")
        assertThat(schemaArg).contains("keywords")
        assertThat(schemaArg).contains("emphasize")
        assertThat(schemaArg).contains("avoid")
        assertThat(schemaArg).contains("summary")
        assertThat(schemaArg).contains("tone")
    }

    @Test
    fun 부분_손상_빈배열_필드누락은_관용적으로_채운다() {
        // given — keywords/emphasize/avoid 누락, tone 빈 문자열. summary는 있으므로 추출 성공.
        val resultJson = """{"summary": "요약만 제대로 옴", "tone": ""}"""

        // when
        val result = extractor(resultJson = resultJson).first.extract("공고", null, null)

        // then
        val strategy = (result as StrategyExtraction.Extracted).strategy
        assertThat(strategy.keywords).isEmpty()
        assertThat(strategy.emphasize).isEmpty()
        assertThat(strategy.avoid).isEmpty()
        assertThat(strategy.tone).isEmpty()
        assertThat(strategy.summary).isEqualTo("요약만 제대로 옴")
    }

    @Test
    fun summary가_비면_Unavailable로_폴백한다() {
        // given — summary 누락은 전략의 핵심 결여 → Unavailable.
        val result = extractor(resultJson = """{"keywords": ["x"], "summary": ""}""").first.extract("공고", null, null)

        // then
        assertThat(result).isEqualTo(StrategyExtraction.Unavailable)
    }

    @Test
    fun CLI_비정상_종료는_Unavailable로_폴백한다() {
        // when
        val result = extractor(exitCode = 1).first.extract("공고", null, null)

        // then
        assertThat(result).isEqualTo(StrategyExtraction.Unavailable)
    }

    @Test
    fun 깨진_응답은_Unavailable로_폴백한다() {
        // when
        val result = extractor(rawStdout = "not-json").first.extract("공고", null, null)

        // then
        assertThat(result).isEqualTo(StrategyExtraction.Unavailable)
    }
}
