package watson.resumaker.generation.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * LLM 호출 공급자 선택(구현 설계 §12 "AI 공급자=교체 가능"). `cli`=Claude CLI 셸 호출(로컬 개발: 구독 토큰으로 비용 0),
 * `api`=Anthropic Messages API 직접 호출(운영: claude CLI 프로세스를 띄우지 않아 메모리 부담 0). 차이는 env로만 가른다.
 */
@ConfigurationProperties(prefix = "resumaker.llm")
data class LlmProperties(
    /** "cli"(기본) 또는 "api". */
    val provider: String = "cli",
)

/**
 * Anthropic Messages API 직접 호출 설정(provider=api일 때만 사용). 구조화 출력은 tool use 강제로 얻는다
 * (스키마=tool input_schema, tool_choice로 그 tool을 강제 → 응답 content의 tool_use.input이 파싱된 결과 JSON).
 *
 * 비밀(apiKey)·프롬프트·결과는 로깅하지 않는다. 운영에서 모델·max-tokens·타임아웃만 조정한다.
 */
@ConfigurationProperties(prefix = "resumaker.anthropic-api")
data class AnthropicApiProperties(
    /** Anthropic API 키(x-api-key 헤더). 구독 토큰은 직접 API에 쓸 수 없다 — 종량 API 키만 유효. */
    val apiKey: String = "",
    /** Messages API 엔드포인트. */
    val baseUrl: String = "https://api.anthropic.com/v1/messages",
    /** anthropic-version 헤더 값. */
    val version: String = "2023-06-01",
    /** 사용할 모델 id(CLI와 동일 기본값 유지). */
    val model: String = "claude-sonnet-4-5",
    /** 응답 출력 토큰 상한(필수 파라미터). 이력서/포트폴리오 구조화 결과가 잘리지 않게 넉넉히 둔다. */
    val maxTokens: Int = 16000,
    /** 한 호출의 최대 대기 시간. 초과 시 호출 실패로 전파한다. */
    val timeout: Duration = Duration.ofSeconds(120),
)
