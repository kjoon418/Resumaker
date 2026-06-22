package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * [ClaudeCliClient] 단위 테스트. **실제 `claude` CLI를 호출하지 않는다** — fake [ProcessRunner]가 canned
 * envelope를 돌려준다(구현 설계 §10, 비용 0·결정성).
 *
 * 실측 envelope 형태(--json-schema): {"type":"result","is_error":false, ... ,"result":"","structured_output":{...}}.
 * 구조화 결과는 `structured_output`(이미 파싱된 JSON)에 담기고 `result`는 빈 문자열이다. 스키마 미사용/구버전
 * 호환을 위해 `result` 텍스트 폴백도 검증한다.
 */
class ClaudeCliClientTest {

    private val objectMapper = ObjectMapper()
    private val properties = ClaudeCliProperties(executablePath = "claude", model = "test-model")

    private fun client(runner: ProcessRunner) = ClaudeCliClient(runner, properties, objectMapper)

    private fun fakeRunner(
        exitCode: Int = 0,
        stdout: String = "",
        stderr: String = "",
        timeoutFlag: Boolean = false,
        executionError: Boolean = false,
        capture: ((List<String>, String) -> Unit)? = null,
    ): ProcessRunner = object : ProcessRunner {
        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
            capture?.invoke(command, stdin)
            if (timeoutFlag) throw ProcessTimeoutException("timeout")
            if (executionError) throw ProcessExecutionException("not found")
            return ProcessResult(exitCode, stdout, stderr)
        }
    }

    /** 실측 envelope: 구조화 결과는 structured_output(이미 파싱된 JSON 노드)에, result는 빈 문자열로 담긴다. */
    private fun envelope(structuredJson: String, isError: Boolean = false): String =
        objectMapper.writeValueAsString(
            mapOf(
                "type" to "result",
                "is_error" to isError,
                "result" to "",
                "structured_output" to objectMapper.readTree(structuredJson),
            ),
        )

    /** 폴백 검증용: structured_output 없이 result에 JSON 텍스트만 담는다(스키마 미사용/구버전 CLI). */
    private fun resultTextEnvelope(resultText: String, isError: Boolean = false): String =
        objectMapper.writeValueAsString(
            mapOf("type" to "result", "is_error" to isError, "result" to resultText),
        )

    @Test
    fun 정상_envelope에서_structured_output을_그대로_반환한다() {
        // given — structured_output에 구조화된 JSON 객체가 이미 파싱돼 담긴다.
        val runner = fakeRunner(stdout = envelope("""{"sections":[{"name":"요약"}]}"""))

        // when
        val node = client(runner).complete(prompt = "p", jsonSchema = "{}")

        // then
        assertThat(node.get("sections").isArray).isTrue()
        assertThat(node.get("sections")[0].get("name").asText()).isEqualTo("요약")
    }

    @Test
    fun structured_output이_배열이면_그대로_반환한다() {
        // given — 배열 스키마 응답: structured_output이 최상위 배열 노드다.
        val runner = fakeRunner(stdout = envelope("""[{"id":1},{"id":2}]"""))

        // when
        val node = client(runner).complete(prompt = "p", jsonSchema = "{}")

        // then
        assertThat(node.isArray).isTrue()
        assertThat(node).hasSize(2)
        assertThat(node[0].get("id").asInt()).isEqualTo(1)
    }

    @Test
    fun structured_output이_없으면_result_텍스트로_폴백_파싱한다() {
        // given — 스키마 미사용/구버전: structured_output 없이 result에 JSON 텍스트가 담긴다.
        val runner = fakeRunner(stdout = resultTextEnvelope("""{"sections":[{"name":"폴백"}]}"""))

        // when
        val node = client(runner).complete(prompt = "p", jsonSchema = "{}")

        // then
        assertThat(node.get("sections")[0].get("name").asText()).isEqualTo("폴백")
    }

    @Test
    fun structured_output도_result도_없으면_예외로_전파된다() {
        // given — structured_output 부재 + result 빈 문자열.
        val runner = fakeRunner(
            stdout = objectMapper.writeValueAsString(
                mapOf("type" to "result", "is_error" to false, "result" to ""),
            ),
        )

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 호출_명령에_핵심_인자가_포함된다() {
        // given
        var capturedCommand: List<String>? = null
        var capturedStdin: String? = null
        val runner = fakeRunner(stdout = envelope("""{"ok":true}""")) { cmd, stdin ->
            capturedCommand = cmd
            capturedStdin = stdin
        }

        // when
        client(runner).complete(prompt = "프롬프트 본문", jsonSchema = "SCHEMA")

        // then — -p, --model, --output-format json, --json-schema가 포함되고 프롬프트는 stdin으로 전달된다.
        assertThat(capturedCommand).contains("-p", "--model", "test-model", "--output-format", "json", "--json-schema", "SCHEMA")
        assertThat(capturedStdin).isEqualTo("프롬프트 본문")
    }

    @Test
    fun 멀티라인_스키마는_한_줄로_펴서_인자에_전달된다() {
        // given — Windows에서 claude.cmd(cmd.exe 경유)는 인자 안 개행에서 명령행을 잘라 JSON을 truncate시킨다.
        // trimIndent()로 개행이 보존된 멀티라인 스키마가 그대로 전달되면 CLI가 "not valid JSON"으로 즉시 실패한다.
        var capturedCommand: List<String>? = null
        val runner = fakeRunner(stdout = envelope("""{"ok":true}""")) { cmd, _ -> capturedCommand = cmd }
        val multilineSchema = """
            {
              "type": "object",
              "required": ["sections"]
            }
        """.trimIndent()

        // when
        client(runner).complete(prompt = "p", jsonSchema = multilineSchema)

        // then — --json-schema 인자에 개행이 없고(한 줄), 토큰은 보존된다.
        val command = capturedCommand!!
        val schemaArg = command[command.indexOf("--json-schema") + 1]
        assertThat(schemaArg).doesNotContain("\n").doesNotContain("\r")
        assertThat(schemaArg).contains("\"type\"", "\"object\"", "\"sections\"")
    }

    @Test
    fun 비정상_종료코드는_예외로_전파된다() {
        // given
        val runner = fakeRunner(exitCode = 1, stderr = "boom")

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 비정상_종료시_stderr가_예외_메시지에_잘려_포함된다() {
        // given (LOW-1) — 운영 진단성: stderr를 앞 500자만 잘라 메시지에 싣는다.
        val longStderr = "E".repeat(700)
        val runner = fakeRunner(exitCode = 2, stderr = longStderr)

        // when and then — 메시지에 stderr 앞부분이 포함되되 전체 길이를 넘기지 않는다.
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
            .hasMessageContaining("E".repeat(500))
            .hasMessageContaining("이하 생략")
    }

    @Test
    fun envelope가_깨진_JSON이면_예외로_전파된다() {
        // given
        val runner = fakeRunner(stdout = "not-json")

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun is_error가_true면_예외로_전파된다() {
        // given
        val runner = fakeRunner(stdout = envelope("""{"x":1}""", isError = true))

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun structured_output과_result_필드가_모두_없으면_예외로_전파된다() {
        // given — 두 필드 모두 부재.
        val runner = fakeRunner(
            stdout = objectMapper.writeValueAsString(mapOf("type" to "result", "is_error" to false)),
        )

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun structured_output이_스칼라이고_result가_비면_예외로_전파된다() {
        // given — structured_output이 객체/배열이 아닌 스칼라(문자열)로 오고 result는 빈 문자열.
        // 스키마는 항상 객체/배열을 강제하지만, 방어적으로 스칼라는 그대로 반환하지 않고 폴백→예외로 빠져야 한다.
        val runner = fakeRunner(stdout = envelope(""""그냥문자열""""))

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun structured_output이_없고_result_폴백이_올바른_JSON이_아니면_예외로_전파된다() {
        // given — structured_output 부재 + result는 텍스트지만 JSON이 아님.
        val runner = fakeRunner(stdout = resultTextEnvelope("이건 JSON이 아니에요"))

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 타임아웃은_예외로_전파된다() {
        // given
        val runner = fakeRunner(timeoutFlag = true)

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 실행_실패는_예외로_전파된다() {
        // given — 실행 파일 없음 등
        val runner = fakeRunner(executionError = true)

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }
}
