package watson.resumaker.template.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.generation.infrastructure.ClaudeCliClient
import watson.resumaker.generation.infrastructure.ClaudeCliProperties
import watson.resumaker.generation.infrastructure.ProcessResult
import watson.resumaker.generation.infrastructure.ProcessRunner
import watson.resumaker.template.application.TemplateInterpretation
import watson.resumaker.template.domain.SectionCharacter
import java.time.Duration

/**
 * [ClaudeCliResumeTemplateInterpreter] 단위 테스트. **실제 CLI 미호출** — fake [ProcessRunner].
 * Interpreted 파싱 성공 / 에러·파싱불가 → Unavailable graceful 폴백을 검증한다.
 */
class ClaudeCliResumeTemplateInterpreterTest {

    private val objectMapper = ObjectMapper()
    private val properties = ClaudeCliProperties()

    private fun interpreter(
        exitCode: Int = 0,
        resultJson: String? = null,
        rawStdout: String? = null,
    ): ClaudeCliResumeTemplateInterpreter {
        val runner = object : ProcessRunner {
            override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
                val stdout = rawStdout ?: objectMapper.writeValueAsString(
                    mapOf("type" to "result", "is_error" to false, "result" to (resultJson ?: "{}")),
                )
                return ProcessResult(exitCode, stdout, "")
            }
        }
        return ClaudeCliResumeTemplateInterpreter(ClaudeCliClient(runner, properties, objectMapper))
    }

    @Test
    fun 정상_결과는_섹션_정의로_해석된다() {
        // given
        val resultJson = """
            {"sections": [
              {"name": "자기소개", "character": "SUMMARY", "required": true},
              {"name": "주요 경력", "character": "CAREER", "required": false}
            ]}
        """.trimIndent()

        // when
        val result = interpreter(resultJson = resultJson).interpret("회사 양식 텍스트")

        // then
        assertThat(result).isInstanceOf(TemplateInterpretation.Interpreted::class.java)
        val sections = (result as TemplateInterpretation.Interpreted).sections
        assertThat(sections).hasSize(2)
        assertThat(sections[0].name).isEqualTo("자기소개")
        assertThat(sections[0].character).isEqualTo(SectionCharacter.SUMMARY)
        assertThat(sections[1].character).isEqualTo(SectionCharacter.CAREER)
    }

    @Test
    fun CLI_비정상_종료는_Unavailable로_폴백한다() {
        // given — exitCode != 0 → ClaudeCliException → 폴백
        // when
        val result = interpreter(exitCode = 1).interpret("텍스트")

        // then
        assertThat(result).isEqualTo(TemplateInterpretation.Unavailable)
    }

    @Test
    fun 깨진_응답은_Unavailable로_폴백한다() {
        // given — envelope 자체가 JSON 아님
        // when
        val result = interpreter(rawStdout = "not-json").interpret("텍스트")

        // then
        assertThat(result).isEqualTo(TemplateInterpretation.Unavailable)
    }

    @Test
    fun 섹션이_0개면_Unavailable로_폴백한다() {
        // given
        // when
        val result = interpreter(resultJson = """{"sections": []}""").interpret("텍스트")

        // then
        assertThat(result).isEqualTo(TemplateInterpretation.Unavailable)
    }

    @Test
    fun sections_필드가_없으면_Unavailable로_폴백한다() {
        // given
        // when
        val result = interpreter(resultJson = """{"other": 1}""").interpret("텍스트")

        // then
        assertThat(result).isEqualTo(TemplateInterpretation.Unavailable)
    }
}
