package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * 공용 Claude CLI 클라이언트(구현 설계 §12). (프롬프트 + JSON 스키마) → 구조화된 결과(파싱된 JSON).
 *
 * **개발 단계 비용 0 제약:** 런타임 LLM 호출을 Anthropic API 직접 호출이 아니라 **Claude CLI 셸 호출**로 한다.
 * 형태: `claude -p --model <m> --output-format json --json-schema <schema>` + 프롬프트는 stdin으로 전달.
 *
 * **CLI envelope(실측 확인):** `claude -p --output-format json`은 한 줄 JSON 객체를 stdout으로 낸다:
 * ```
 * {"type":"result","subtype":"success","is_error":false, ... ,"result":"","structured_output":{...파싱된 JSON...}}
 * ```
 * `--json-schema`로 구조화 출력을 유도하면 **구조화 결과는 최상위 `structured_output` 필드**에 이미 파싱된
 * JSON 객체/배열로 담기고, `result`는 **빈 문자열("")**이 된다(실측). 따라서 이 클라이언트는 우선
 * `structured_output` 노드를 **그대로**(재파싱 없이) 반환한다. 스키마를 쓰지 않는 구버전/텍스트 응답 호환을 위해
 * `structured_output`이 없고 `result`가 비어있지 않은 텍스트면 `result`를 JSON으로 파싱해 폴백 반환한다.
 *
 * **방어적 파싱:** 비정상 종료(exitCode != 0)·타임아웃·깨진 envelope JSON·`is_error == true`·구조화 결과 부재
 * (`structured_output`도 없고 `result`도 비었거나 JSON이 아님) → 모두 명확한 [ClaudeCliException]으로 전파한다
 * (호출자가 graceful 폴백/부분 실패로 처리).
 *
 * 실제 프로세스 실행은 [ProcessRunner] seam 뒤로 격리해 테스트가 실제 CLI를 호출하지 않게 한다(구현 설계 §10).
 *
 * **로깅 정책:** 프롬프트·스키마·결과 본문은 PII/비밀을 포함할 수 있어 로깅하지 않는다. 예외 메시지에도 본문을 싣지 않는다.
 */
@Component
class ClaudeCliClient(
    private val processRunner: ProcessRunner,
    private val properties: ClaudeCliProperties,
    private val objectMapper: ObjectMapper,
    // 아래 셋은 provider=api 분기에서만 쓴다. 단위 테스트(CLI 경로)는 기본값으로 생성하므로 영향이 없다.
    private val llmProperties: LlmProperties = LlmProperties(),
    private val anthropicApiProperties: AnthropicApiProperties = AnthropicApiProperties(),
    private val httpJsonClient: HttpJsonClient? = null,
) {

    /**
     * 프롬프트와 JSON 스키마로 Claude CLI를 호출해 구조화된 결과 JSON을 반환한다.
     *
     * @param prompt     모델에 보낼 프롬프트(stdin으로 전달).
     * @param jsonSchema 출력 구조를 고정하는 JSON Schema 문자열(`--json-schema` 인자로 전달).
     * @return envelope의 `structured_output` 노드(있으면 그대로), 없으면 `result` 텍스트를 파싱한 JSON 트리.
     * @throws ClaudeCliException 호출/파싱 실패의 모든 경우.
     */
    fun complete(prompt: String, jsonSchema: String): JsonNode {
        // provider=api면 claude CLI 프로세스를 띄우지 않고 Anthropic Messages API를 직접 호출한다(운영 저메모리).
        // 계약(반환 JsonNode·예외 타입 ClaudeCliException)은 동일하므로 호출 어댑터는 분기를 알 필요가 없다.
        if (llmProperties.provider.equals("api", ignoreCase = true)) {
            return completeViaApi(prompt, jsonSchema)
        }

        val command = listOf(
            properties.executablePath,
            "-p",
            "--model", properties.model,
            "--output-format", "json",
            // 스키마는 한 줄로 펴서 인자에 싣는다. Windows에서 `claude`는 `claude.cmd`(PATHEXT)로 해석돼
            // ProcessBuilder가 cmd.exe를 거치는데, cmd.exe는 **인자 안의 개행에서 명령행을 잘라버린다**. 멀티라인
            // 스키마(`trimIndent()`로 개행 보존)를 그대로 넘기면 JSON이 첫 줄에서 truncate돼 CLI가 "not valid JSON"으로
            // 즉시 실패한다(생성이 pending처럼 보이다 503). JSON은 토큰 사이 공백에 무관하고 스키마 문자열 값 안에는
            // 리터럴 개행이 없으므로(있으면 애초에 무효 JSON), 개행을 공백으로 바꿔 한 줄로 만들면 의미가 보존된다.
            // Linux/docker(execvp, cmd.exe 미경유)에는 영향이 없다.
            "--json-schema", jsonSchema.toSingleLine(),
        )

        val result = try {
            processRunner.run(command, stdin = prompt, timeout = properties.timeout)
        } catch (e: ProcessTimeoutException) {
            throw ClaudeCliException("Claude CLI 호출이 제한 시간을 초과했어요.", e)
        } catch (e: ProcessExecutionException) {
            throw ClaudeCliException("Claude CLI를 실행하지 못했어요.", e)
        }

        if (result.exitCode != 0) {
            // 운영 진단성: stderr(운영 메시지로 간주)를 앞 500자만 잘라 포함한다.
            // stdout/프롬프트/결과(사용자 콘텐츠)는 절대 포함하지 않는다(로깅 정책).
            throw ClaudeCliException(
                "Claude CLI가 비정상 종료했어요(코드 ${result.exitCode}). stderr: ${truncateForDiagnostics(result.stderr)}",
            )
        }

        val envelope = try {
            objectMapper.readTree(result.stdout)
        } catch (e: Exception) {
            throw ClaudeCliException("Claude CLI 응답을 해석할 수 없어요(envelope JSON 파싱 실패).", e)
        }

        if (envelope == null || !envelope.isObject) {
            throw ClaudeCliException("Claude CLI 응답 형식이 올바르지 않아요(객체가 아님).")
        }
        if (envelope.path("is_error").asBoolean(false)) {
            throw ClaudeCliException("Claude CLI가 오류를 보고했어요.")
        }

        // 우선순위 1: --json-schema 사용 시 구조화 결과는 최상위 structured_output에 이미 파싱된 채 담긴다.
        // 존재하고 객체/배열이면 재파싱 없이 그대로 반환한다.
        val structuredOutput = envelope.get("structured_output")
        if (structuredOutput != null && !structuredOutput.isNull &&
            (structuredOutput.isObject || structuredOutput.isArray)
        ) {
            return structuredOutput
        }

        // 폴백(견고성): structured_output이 없거나 null인데 result가 비어있지 않은 텍스트면
        // 기존처럼 result 텍스트를 JSON으로 파싱해 반환한다(스키마 미사용/구버전 CLI 호환).
        val resultNode = envelope.get("result")
        if (resultNode == null || !resultNode.isTextual || resultNode.asText().isEmpty()) {
            throw ClaudeCliException("Claude CLI 응답에 구조화 결과(structured_output/result)가 없어요.")
        }

        return try {
            objectMapper.readTree(resultNode.asText())
        } catch (e: Exception) {
            throw ClaudeCliException("Claude CLI 결과가 올바른 JSON이 아니에요.", e)
        }
    }

    /**
     * 개행(CRLF/CR/LF)을 공백으로 바꿔 스키마를 한 줄로 편다. cmd.exe 인자 개행 truncation 회피용(위 [complete] 주석).
     * 연속 개행/주변 공백이 늘어나도 JSON 의미에는 영향이 없다(토큰 사이 공백 무관).
     */
    private fun String.toSingleLine(): String = replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')

    private fun truncateForDiagnostics(stderr: String): String {
        val trimmed = stderr.trim()
        if (trimmed.isEmpty()) return "(없음)"
        return if (trimmed.length <= MAX_STDERR_DIAGNOSTIC_LENGTH) {
            trimmed
        } else {
            trimmed.take(MAX_STDERR_DIAGNOSTIC_LENGTH) + "…(이하 생략)"
        }
    }

    /**
     * Anthropic Messages API 직접 호출 경로. 구조화 출력은 **tool use 강제**로 얻는다: 스키마를 단일 tool의
     * input_schema로 싣고 tool_choice로 그 tool을 강제하면, 응답 content의 `tool_use.input`에 스키마에 맞는 파싱된
     * JSON이 담긴다(이미 객체/배열). 이를 그대로 반환해 CLI 경로의 `structured_output`과 동일한 계약을 만족한다.
     *
     * 실패(타임아웃·연결 실패·비정상 상태코드·envelope 파싱 불가·tool_use 부재)는 모두 [ClaudeCliException]으로
     * 전파한다(호출 어댑터가 graceful 폴백·503 매핑으로 처리, 기존 흐름과 동일). 본문(프롬프트·결과)·API 키는 로깅하지 않는다.
     */
    private fun completeViaApi(prompt: String, jsonSchema: String): JsonNode {
        val http = httpJsonClient
            ?: throw ClaudeCliException("HTTP 클라이언트가 구성되지 않았어요(provider=api인데 주입 누락).")

        val schemaNode = try {
            objectMapper.readTree(jsonSchema)
        } catch (e: Exception) {
            throw ClaudeCliException("출력 스키마를 해석할 수 없어요(JSON 파싱 실패).", e)
        }

        val requestBody = objectMapper.writeValueAsString(
            mapOf(
                "model" to anthropicApiProperties.model,
                "max_tokens" to anthropicApiProperties.maxTokens,
                // 단일 tool을 강제 호출시켜 구조화 출력을 받는다.
                "tool_choice" to mapOf("type" to "tool", "name" to STRUCTURED_OUTPUT_TOOL_NAME),
                "tools" to listOf(
                    mapOf(
                        "name" to STRUCTURED_OUTPUT_TOOL_NAME,
                        "description" to "주어진 스키마에 정확히 맞는 구조화 결과를 반환한다.",
                        "input_schema" to schemaNode,
                    ),
                ),
                "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            ),
        )

        val headers = mapOf(
            "x-api-key" to anthropicApiProperties.apiKey,
            "anthropic-version" to anthropicApiProperties.version,
            "content-type" to "application/json",
        )

        val response = try {
            http.postJson(anthropicApiProperties.baseUrl, headers, requestBody, anthropicApiProperties.timeout)
        } catch (e: HttpJsonTimeoutException) {
            throw ClaudeCliException("Anthropic API 호출이 제한 시간을 초과했어요.", e)
        } catch (e: HttpJsonExecutionException) {
            throw ClaudeCliException("Anthropic API를 호출하지 못했어요.", e)
        }

        if (response.statusCode !in 200..299) {
            // 본문은 사용자 콘텐츠를 포함할 수 있어 싣지 않고 상태코드만 남긴다(로깅/예외 정책).
            throw ClaudeCliException("Anthropic API가 오류 상태를 반환했어요(코드 ${response.statusCode}).")
        }

        val envelope = try {
            objectMapper.readTree(response.body)
        } catch (e: Exception) {
            throw ClaudeCliException("Anthropic API 응답을 해석할 수 없어요(JSON 파싱 실패).", e)
        }

        if (envelope == null || !envelope.isObject) {
            throw ClaudeCliException("Anthropic API 응답 형식이 올바르지 않아요(객체가 아님).")
        }

        // content 배열에서 tool_use 블록을 찾아 그 input(파싱된 구조화 JSON)을 반환한다.
        val content = envelope.get("content")
        if (content != null && content.isArray) {
            for (block in content) {
                if (block.path("type").asText() == "tool_use") {
                    val input = block.get("input")
                    if (input != null && (input.isObject || input.isArray)) {
                        return input
                    }
                }
            }
        }
        throw ClaudeCliException("Anthropic API 응답에 구조화 결과(tool_use)가 없어요.")
    }

    companion object {
        /** 예외 메시지에 포함할 stderr 최대 길이(운영 진단용, 앞부분만). */
        private const val MAX_STDERR_DIAGNOSTIC_LENGTH = 500

        /** 구조화 출력을 강제하기 위한 가상 tool 이름(API 경로). */
        private const val STRUCTURED_OUTPUT_TOOL_NAME = "structured_output"
    }
}

/**
 * Claude CLI 호출/파싱 실패. 호출자(생성 어댑터·해석 어댑터)가 부분 실패·graceful 폴백으로 변환한다.
 */
class ClaudeCliException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
