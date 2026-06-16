package watson.resumaker.generation.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Claude CLI 어댑터 외부 설정(구현 설계 §12, §7 "구체 수치는 @ConfigurationProperties로 외부화").
 *
 * - [executablePath]: `claude` 실행 파일 경로(PATH에 있으면 "claude"로 충분).
 * - [model]: 사용할 모델 별칭(기본은 합리적 Claude Sonnet 계열 — 비용·품질 균형).
 * - [timeout]: 한 호출의 최대 대기 시간. 초과 시 호출 실패로 전파한다.
 *
 * 비밀·PII를 로깅하지 않는다(프롬프트·결과 본문은 로깅 금지). 운영에서 모델·타임아웃·경로만 조정한다.
 */
@ConfigurationProperties(prefix = "resumaker.claude-cli")
data class ClaudeCliProperties(
    val executablePath: String = "claude",
    val model: String = "claude-sonnet-4-5",
    val timeout: Duration = Duration.ofSeconds(120),
)
