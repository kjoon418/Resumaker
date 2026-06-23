package watson.resumaker.quality.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.TargetSnapshot
import watson.resumaker.generation.infrastructure.ClaudeCliClient
import watson.resumaker.generation.infrastructure.ClaudeCliProperties
import watson.resumaker.generation.infrastructure.ProcessResult
import watson.resumaker.generation.infrastructure.ProcessRunner
import watson.resumaker.quality.application.QualityImprovementInput
import java.time.Duration
import java.util.UUID

/**
 * [ClaudeCliQualityImprovementAdapter] 단위 테스트. **실제 CLI 미호출** — fake [ProcessRunner]가 canned envelope를
 * 돌려준다. 입력→프롬프트 구성(신뢰성 절대 규칙·원본·개선 방향 포함)·결과 파싱→후보 매핑을 검증한다.
 *
 * **.cmd 개행 truncation 회귀:** [ClaudeCliClient]가 스키마를 한 줄로 펴 넘기는 경로를 그대로 탄다. 멀티라인 스키마
 * 인자가 개행에서 잘리지 않아야 CLI가 정상 envelope를 돌려준다(이 테스트가 그 envelope를 fake로 흉내).
 */
class ClaudeCliQualityImprovementAdapterTest {

    private val objectMapper = ObjectMapper()
    private val properties = ClaudeCliProperties()
    private val expId = ExperienceRecordId(UUID.randomUUID())

    private class CapturingRunner(private val resultJson: String) : ProcessRunner {
        var capturedStdin: String = ""
        var capturedCommand: List<String> = emptyList()
        private val mapper = ObjectMapper()

        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
            capturedStdin = stdin
            capturedCommand = command
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

    private fun adapter(resultJson: String): Pair<ClaudeCliQualityImprovementAdapter, CapturingRunner> {
        val runner = CapturingRunner(resultJson)
        val cli = ClaudeCliClient(runner, properties, objectMapper)
        return ClaudeCliQualityImprovementAdapter(cli) to runner
    }

    private fun input() = QualityImprovementInput(
        definitionKey = "section-0-요약",
        sectionKind = SectionKind.SUMMARY,
        originalContent = "결제 시스템을 담당했다.",
        criteria = listOf("약한 동사를 강한 행동·성취 동사로 바꾸면 더 좋아요"),
        target = TargetSnapshot("백엔드 신입", "토스", "서버 개발자"),
        experiences = listOf(
            ExperienceSnapshot(expId, "결제 경험", "결제 시스템을 설계하고 운영했다.", null, null, null, listOf("Kotlin")),
        ),
        sourceExperienceIds = listOf(expId),
    )

    @Test
    fun 프롬프트에_신뢰성_절대규칙과_원본과_개선방향이_포함된다() {
        // given
        val (adapter, runner) = adapter("""{"sections":[]}""")

        // when
        adapter.improve(input())

        // then — 공유 가드레일 문구·원본 항목·개선 방향·원본 사실 보존 지시가 프롬프트에 들어간다.
        assertThat(runner.capturedStdin).contains("지어내지 마세요")
        assertThat(runner.capturedStdin).contains("다듬을 항목(원본")
        assertThat(runner.capturedStdin).contains("결제 시스템을 담당했다.")
        assertThat(runner.capturedStdin).contains("약한 동사를 강한 행동")
        assertThat(runner.capturedStdin).contains("그대로 보존")
    }

    @Test
    fun 스키마_인자는_개행없이_단일행으로_전달된다_truncation_회귀() {
        // given (ccf323e) — Windows cmd.exe가 인자 개행에서 명령행을 자르는 문제 회피. 스키마 인자에 개행이 없어야 한다.
        val (adapter, runner) = adapter("""{"sections":[]}""")

        // when
        adapter.improve(input())

        // then — --json-schema 다음 인자(스키마)에 개행이 없다.
        val schemaArgIndex = runner.capturedCommand.indexOf("--json-schema") + 1
        assertThat(schemaArgIndex).isGreaterThan(0)
        assertThat(runner.capturedCommand[schemaArgIndex]).doesNotContain("\n")
    }

    @Test
    fun 다듬은_후보를_항목과_근거로_매핑한다() {
        // given — CLI가 다듬은 단일 항목을 근거와 함께 돌려줬다.
        val resultJson = """
            {
              "sections": [
                {
                  "definitionKey": "section-0-요약",
                  "sectionKind": "SUMMARY",
                  "content": "결제 시스템을 설계·운영했어요.",
                  "succeeded": true,
                  "sourceExperienceIds": ["${expId.value}"],
                  "factGroundings": []
                }
              ]
            }
        """.trimIndent()
        val (adapter, _) = adapter(resultJson)

        // when
        val result = adapter.improve(input())

        // then
        assertThat(result).isNotNull
        assertThat(result!!.definitionKey).isEqualTo("section-0-요약")
        assertThat(result.succeeded).isTrue()
        assertThat(result.content).isEqualTo("결제 시스템을 설계·운영했어요.")
        assertThat(result.sourceExperienceIds).containsExactly(expId)
    }

    @Test
    fun succeeded_누락이면_실패로_매핑된다() {
        // given (안전 기본값 = 실패).
        val resultJson = """
            {"sections":[{"definitionKey":"section-0-요약","sectionKind":"SUMMARY","content":"x","sourceExperienceIds":[],"factGroundings":[]}]}
        """.trimIndent()
        val (adapter, _) = adapter(resultJson)

        // when
        val result = adapter.improve(input())

        // then
        assertThat(result!!.succeeded).isFalse()
    }
}
