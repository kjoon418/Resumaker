package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * [ClaudeCliClient]의 provider=api(Anthropic Messages API 직접 호출) 분기 단위 테스트. **실제 HTTP를 치지 않는다** —
 * fake [HttpJsonClient]가 canned 응답을 돌려준다(구현 설계 §10, 비용 0·결정성). CLI 경로와 같은 계약(반환 JsonNode·
 * 예외 ClaudeCliException)을 검증한다. 구조화 출력은 응답 content의 tool_use.input으로 온다.
 */
class ClaudeCliClientApiPathTest {

    private val objectMapper = ObjectMapper()
    private val apiProperties = AnthropicApiProperties(apiKey = "test-key", model = "test-model")

    /** CLI 경로가 절대 호출되지 않음을 보장하는 stub(api 분기 검증용). */
    private val failingProcessRunner = object : ProcessRunner {
        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult =
            throw AssertionError("api 경로에서 ProcessRunner(CLI)가 호출되면 안 된다")
    }

    private fun client(http: HttpJsonClient, apiProps: AnthropicApiProperties = apiProperties) = ClaudeCliClient(
        processRunner = failingProcessRunner,
        properties = ClaudeCliProperties(),
        objectMapper = objectMapper,
        llmProperties = LlmProperties(provider = "api"),
        anthropicApiProperties = apiProps,
        httpJsonClient = http,
    )

    private fun fakeHttp(
        statusCode: Int = 200,
        responseBody: String = "",
        timeoutFlag: Boolean = false,
        executionError: Boolean = false,
        capture: ((String, Map<String, String>, String) -> Unit)? = null,
    ): HttpJsonClient = object : HttpJsonClient {
        override fun postJson(url: String, headers: Map<String, String>, body: String, timeout: Duration): HttpJsonResponse {
            capture?.invoke(url, headers, body)
            if (timeoutFlag) throw HttpJsonTimeoutException("timeout")
            if (executionError) throw HttpJsonExecutionException("connect failed")
            return HttpJsonResponse(statusCode, responseBody)
        }
    }

    /** Anthropic Messages 응답: 강제된 tool_use 블록의 input에 구조화 JSON이 담긴다. */
    private fun toolUseEnvelope(structuredJson: String, stopReason: String = "tool_use"): String =
        objectMapper.writeValueAsString(
            mapOf(
                "type" to "message",
                "role" to "assistant",
                "stop_reason" to stopReason,
                "content" to listOf(
                    mapOf(
                        "type" to "tool_use",
                        "id" to "toolu_1",
                        "name" to "structured_output",
                        "input" to objectMapper.readTree(structuredJson),
                    ),
                ),
            ),
        )

    @Test
    fun tool_use_input의_구조화_JSON을_반환한다() {
        // given
        val http = fakeHttp(responseBody = toolUseEnvelope("""{"sections":[{"name":"요약"}]}"""))

        // when
        val node = client(http).complete(prompt = "p", jsonSchema = "{}")

        // then
        assertThat(node.get("sections")[0].get("name").asText()).isEqualTo("요약")
    }

    @Test
    fun 배열_구조화_결과도_그대로_반환한다() {
        // given
        val http = fakeHttp(responseBody = toolUseEnvelope("""[{"id":1},{"id":2}]"""))

        // when
        val node = client(http).complete(prompt = "p", jsonSchema = "{}")

        // then
        assertThat(node.isArray).isTrue()
        assertThat(node).hasSize(2)
    }

    @Test
    fun 요청에_헤더와_tool_choice_스키마_프롬프트가_담긴다() {
        // given
        var capturedHeaders: Map<String, String>? = null
        var capturedBody: String? = null
        val http = fakeHttp(responseBody = toolUseEnvelope("""{"ok":true}""")) { _, headers, body ->
            capturedHeaders = headers
            capturedBody = body
        }

        // when
        client(http).complete(prompt = "프롬프트 본문", jsonSchema = """{"type":"object"}""")

        // then — 인증 헤더, tool 강제, 스키마, 모델, 프롬프트가 본문에 포함된다.
        assertThat(capturedHeaders).containsEntry("x-api-key", "test-key").containsEntry("anthropic-version", "2023-06-01")
        val body = objectMapper.readTree(capturedBody)
        assertThat(body.get("model").asText()).isEqualTo("test-model")
        assertThat(body.get("tool_choice").get("type").asText()).isEqualTo("tool")
        assertThat(body.get("tool_choice").get("name").asText()).isEqualTo("structured_output")
        assertThat(body.get("tools")[0].get("input_schema").get("type").asText()).isEqualTo("object")
        assertThat(body.get("messages")[0].get("content").asText()).isEqualTo("프롬프트 본문")
        assertThat(body.has("max_tokens")).isTrue()
    }

    @Test
    fun 비정상_상태코드는_예외로_전파된다() {
        assertThatThrownBy { client(fakeHttp(statusCode = 401, responseBody = "{}")).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 타임아웃은_예외로_전파된다() {
        assertThatThrownBy { client(fakeHttp(timeoutFlag = true)).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 연결_실패는_예외로_전파된다() {
        assertThatThrownBy { client(fakeHttp(executionError = true)).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 깨진_envelope는_예외로_전파된다() {
        assertThatThrownBy { client(fakeHttp(responseBody = "not-json")).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun tool_use가_없으면_예외로_전파된다() {
        // given — content에 text 블록만(거부/비정상). tool_use 부재.
        val body = objectMapper.writeValueAsString(
            mapOf("type" to "message", "content" to listOf(mapOf("type" to "text", "text" to "거부"))),
        )

        // when and then
        assertThatThrownBy { client(fakeHttp(responseBody = body)).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun 스키마가_깨진_JSON이면_예외로_전파된다() {
        assertThatThrownBy { client(fakeHttp(responseBody = toolUseEnvelope("{}"))).complete("p", "not-a-schema{") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun max_tokens로_잘리면_예외로_전파된다() {
        // given — stop_reason=max_tokens인데 valid tool_use.input이 있어도 부분 산출물이므로 실패해야 한다.
        val http = fakeHttp(
            responseBody = toolUseEnvelope("""{"sections":[{"name":"요약"}]}""", stopReason = "max_tokens"),
        )

        // when and then
        assertThatThrownBy { client(http).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun refusal이면_예외로_전파된다() {
        // given — stop_reason=refusal + text-only content.
        val body = objectMapper.writeValueAsString(
            mapOf(
                "type" to "message",
                "stop_reason" to "refusal",
                "content" to listOf(mapOf("type" to "text", "text" to "거부")),
            ),
        )

        // when and then
        assertThatThrownBy { client(fakeHttp(responseBody = body)).complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }

    @Test
    fun api_key가_비면_예외로_전파된다() {
        // given — apiKey가 비어 있으면 원격 호출 전에 즉시 실패한다.
        val blankKeyClient = client(
            fakeHttp(responseBody = toolUseEnvelope("""{"ok":true}""")),
            apiProps = AnthropicApiProperties(apiKey = "", model = "test-model"),
        )

        // when and then
        assertThatThrownBy { blankKeyClient.complete("p", "{}") }
            .isInstanceOf(ClaudeCliException::class.java)
    }
}
