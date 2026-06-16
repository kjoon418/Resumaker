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
 * {"type":"result","subtype":"success","is_error":false, ... ,"result":"<모델 텍스트 출력>", ...}
 * ```
 * 실제 모델 출력은 `result` 필드의 문자열이다. `--json-schema`로 구조화 출력을 유도하면 `result`가
 * 스키마를 만족하는 JSON 문자열이 된다. 이 클라이언트는 envelope에서 `result`를 추출한 뒤 JSON으로 재파싱해
 * 반환한다.
 *
 * **방어적 파싱:** 비정상 종료(exitCode != 0)·타임아웃·깨진 envelope JSON·`is_error == true`·`result` 누락·
 * `result`가 JSON이 아님 → 모두 명확한 [ClaudeCliException]으로 전파한다(호출자가 graceful 폴백/부분 실패로 처리).
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
) {

    /**
     * 프롬프트와 JSON 스키마로 Claude CLI를 호출해 구조화된 결과 JSON을 반환한다.
     *
     * @param prompt     모델에 보낼 프롬프트(stdin으로 전달).
     * @param jsonSchema 출력 구조를 고정하는 JSON Schema 문자열(`--json-schema` 인자로 전달).
     * @return envelope의 `result`를 파싱한 JSON 트리.
     * @throws ClaudeCliException 호출/파싱 실패의 모든 경우.
     */
    fun complete(prompt: String, jsonSchema: String): JsonNode {
        val command = listOf(
            properties.executablePath,
            "-p",
            "--model", properties.model,
            "--output-format", "json",
            "--json-schema", jsonSchema,
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

        val resultNode = envelope.get("result")
        if (resultNode == null || !resultNode.isTextual) {
            throw ClaudeCliException("Claude CLI 응답에 결과(result)가 없어요.")
        }

        return try {
            objectMapper.readTree(resultNode.asText())
        } catch (e: Exception) {
            throw ClaudeCliException("Claude CLI 결과가 올바른 JSON이 아니에요.", e)
        }
    }

    private fun truncateForDiagnostics(stderr: String): String {
        val trimmed = stderr.trim()
        if (trimmed.isEmpty()) return "(없음)"
        return if (trimmed.length <= MAX_STDERR_DIAGNOSTIC_LENGTH) {
            trimmed
        } else {
            trimmed.take(MAX_STDERR_DIAGNOSTIC_LENGTH) + "…(이하 생략)"
        }
    }

    companion object {
        /** 예외 메시지에 포함할 stderr 최대 길이(운영 진단용, 앞부분만). */
        private const val MAX_STDERR_DIAGNOSTIC_LENGTH = 500
    }
}

/**
 * Claude CLI 호출/파싱 실패. 호출자(생성 어댑터·해석 어댑터)가 부분 실패·graceful 폴백으로 변환한다.
 */
class ClaudeCliException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
