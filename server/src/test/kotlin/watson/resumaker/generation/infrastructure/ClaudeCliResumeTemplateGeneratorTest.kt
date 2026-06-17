package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.ResumeTemplateGeneration
import watson.resumaker.generation.application.ResumeTemplateGenerationInput
import watson.resumaker.generation.application.TargetSnapshot
import watson.resumaker.template.domain.SectionCharacter
import java.time.Duration
import java.util.UUID

/**
 * [ClaudeCliResumeTemplateGenerator] 단위 테스트. **실제 CLI 미호출** — fake [ProcessRunner]가 canned
 * envelope를 돌려준다. 프롬프트 구성(구조만·사실 금지)·결과 파싱·graceful 폴백을 검증한다.
 */
class ClaudeCliResumeTemplateGeneratorTest {

    private val objectMapper = ObjectMapper()
    private val properties = ClaudeCliProperties()

    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    /** 구조화 결과를 돌려주는 러너. capturedStdin으로 프롬프트를 들여다본다. */
    private class CannedRunner(private val resultJson: String) : ProcessRunner {
        var capturedStdin: String = ""
        private val mapper = ObjectMapper()
        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
            capturedStdin = stdin
            val envelope = mapper.writeValueAsString(
                mapOf(
                    "type" to "result",
                    "is_error" to false,
                    "result" to "",
                    "structured_output" to mapper.readTree(resultJson),
                ),
            )
            return ProcessResult(0, envelope, "")
        }
    }

    /** 항상 비정상 종료해 CLI 실패를 모사하는 러너(graceful 폴백 검증). */
    private class FailingRunner : ProcessRunner {
        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult =
            ProcessResult(1, "", "boom")
    }

    private fun generatorWith(runner: ProcessRunner): ClaudeCliResumeTemplateGenerator {
        val cli = ClaudeCliClient(runner, properties, objectMapper)
        return ClaudeCliResumeTemplateGenerator(cli)
    }

    private fun input() = ResumeTemplateGenerationInput(
        experiences = listOf(
            ExperienceSnapshot(
                id = exp1,
                title = "백엔드 프로젝트",
                body = "Spring 서버 개발",
                situation = null,
                action = null,
                result = null,
                skillTags = listOf("Kotlin"),
            ),
        ),
        target = TargetSnapshot(recruitDirection = "백엔드 신입", company = "토스", job = "서버 개발자"),
    )

    @Test
    fun 경험과_목표로_섹션_정의_목록을_생성한다() {
        // given
        val resultJson = """
            {
              "sections": [
                {"name": "한 줄 자기소개", "character": "SUMMARY", "required": true},
                {"name": "주요 경력", "character": "CAREER", "required": true}
              ]
            }
        """.trimIndent()
        val generator = generatorWith(CannedRunner(resultJson))

        // when
        val result = generator.generate(input())

        // then
        assertThat(result).isInstanceOf(ResumeTemplateGeneration.Generated::class.java)
        val sections = (result as ResumeTemplateGeneration.Generated).sections
        assertThat(sections.map { it.name }).containsExactly("한 줄 자기소개", "주요 경력")
        assertThat(sections.map { it.character })
            .containsExactly(SectionCharacter.SUMMARY, SectionCharacter.CAREER)
        assertThat(sections.map { it.required }).containsExactly(true, true)
    }

    @Test
    fun 프롬프트는_구조만_만들고_사실_금지를_지시하며_목표와_경험을_담는다() {
        // given
        val runner = CannedRunner("""{"sections":[]}""")
        val generator = generatorWith(runner)

        // when
        generator.generate(input())

        // then
        assertThat(runner.capturedStdin).contains("구조")
        assertThat(runner.capturedStdin).contains("사실 내용")
        assertThat(runner.capturedStdin).contains("백엔드 신입")
        assertThat(runner.capturedStdin).contains("백엔드 프로젝트")
    }

    @Test
    fun CLI_실패면_차단하지_않고_Unavailable로_폴백한다() {
        // given (graceful 폴백, §186)
        val generator = generatorWith(FailingRunner())

        // when
        val result = generator.generate(input())

        // then
        assertThat(result).isEqualTo(ResumeTemplateGeneration.Unavailable)
    }

    @Test
    fun 섹션이_0개면_Unavailable로_폴백한다() {
        // given — 결과 파싱은 되지만 섹션이 비면 사용 불가로 본다(유스케이스가 기본 구조로 진행).
        val generator = generatorWith(CannedRunner("""{"sections":[]}"""))

        // when
        val result = generator.generate(input())

        // then
        assertThat(result).isEqualTo(ResumeTemplateGeneration.Unavailable)
    }

    @Test
    fun 알_수_없는_character는_드롭하고_유효한_섹션만_남긴다() {
        // given — 형식이 깨진 섹션(character 미해석)은 드롭한다.
        val resultJson = """
            {
              "sections": [
                {"name": "요약", "character": "SUMMARY", "required": true},
                {"name": "이상한 섹션", "character": "UNKNOWN", "required": false}
              ]
            }
        """.trimIndent()
        val generator = generatorWith(CannedRunner(resultJson))

        // when
        val result = generator.generate(input())

        // then
        val sections = (result as ResumeTemplateGeneration.Generated).sections
        assertThat(sections.map { it.name }).containsExactly("요약")
    }

    @Test
    fun 한_섹션이_깨져도_나머지_유효_섹션은_살아남는다() {
        // given (LOW-1) — per-section 관용: 이름 길이 초과 등으로 한 섹션만 throw해도 전체가 Unavailable이 되지 않는다.
        // SectionDefinition.of()가 이름 길이를 초과하면 throw한다 — 최대 100자를 넘기는 이름으로 재현.
        val tooLongName = "가".repeat(101) // SectionDefinition MAX_NAME_LENGTH=100 초과
        val resultJson = """
            {
              "sections": [
                {"name": "요약", "character": "SUMMARY", "required": true},
                {"name": "$tooLongName", "character": "CAREER", "required": false},
                {"name": "경력", "character": "CAREER", "required": true}
              ]
            }
        """.trimIndent()
        val generator = generatorWith(CannedRunner(resultJson))

        // when
        val result = generator.generate(input())

        // then — 깨진 섹션 하나만 드롭되고 유효한 두 섹션은 Generated로 살아남는다.
        assertThat(result).isInstanceOf(ResumeTemplateGeneration.Generated::class.java)
        val sections = (result as ResumeTemplateGeneration.Generated).sections
        assertThat(sections.map { it.name }).containsExactly("요약", "경력")
    }
}
