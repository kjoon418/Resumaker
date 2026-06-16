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
 * 실측 envelope 형태: {"type":"result","is_error":false, ... ,"result":"<JSON 문자열>"}.
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

    private fun envelope(resultJson: String, isError: Boolean = false): String =
        objectMapper.writeValueAsString(
            mapOf("type" to "result", "is_error" to isError, "result" to resultJson),
        )

    @Test
    fun 정상_envelope에서_result를_JSON으로_파싱한다() {
        // given — result 필드에 구조화된 JSON 문자열이 담긴다.
        val runner = fakeRunner(stdout = envelope("""{"sections":[{"name":"요약"}]}"""))

        // when
        val node = client(runner).complete(prompt = "p", jsonSchema = "{}")

        // then
        assertThat(node.get("sections").isArray).isTrue()
        assertThat(node.get("sections")[0].get("name").asText()).isEqualTo("요약")
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
    fun result가_없으면_예외로_전파된다() {
        // given
        val runner = fakeRunner(
            stdout = objectMapper.writeValueAsString(mapOf("type" to "result", "is_error" to false)),
        )

        // when and then
        assertThatThrownBy { client(runner).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun result가_올바른_JSON이_아니면_예외로_전파된다() {
        // given — result는 텍스트지만 JSON이 아님
        val runner = fakeRunner(stdout = envelope("이건 JSON이 아니에요"))

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
