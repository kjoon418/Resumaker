package watson.resumaker.target.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 작성 전략 추출 워커 수치 외부 설정([GenerationJobProperties]와 동일한 패턴).
 *
 * - [pollIntervalMs]: 워커 폴링 주기(ms). 매 틱마다 고아 EXTRACTING 회수 + 가장 오래된 PENDING 1건 추출.
 *   @Scheduled(fixedDelay)라 한 작업 처리가 끝난 뒤 이 간격을 두고 다음 틱이 돈다(겹치지 않음).
 * - [staleRunningTimeout]: EXTRACTING이 이 시간을 넘기면 죽은 워커가 남긴 고아로 보고 FAILED로 회수한다
 *   (영원히 EXTRACTING 방지). 재추출하지 않는다 — 사용자가 재시도하거나 생성 시 원문 폴백을 쓴다.
 *
 * 기본값(2초·5분)은 생성 작업 워커와 동일한 MVP 적정선이다. 전략 추출은 생성 일일 한도(quota)와 무관하다(차감 없음).
 */
@ConfigurationProperties(prefix = "resumaker.target-strategy")
data class TargetStrategyProperties(
    val pollIntervalMs: Long = 2000,
    val staleRunningTimeout: Duration = Duration.ofMinutes(5),
)
