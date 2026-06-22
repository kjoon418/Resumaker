package watson.resumaker.generation.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 비동기 생성 작업 워커 수치 외부 설정([GenerationQuotaProperties]와 동일한 패턴).
 *
 * - [pollIntervalMs]: 워커 폴링 주기(ms). 매 틱마다 고아 RUNNING 회수 + 가장 오래된 PENDING 1건 처리.
 *   @Scheduled(fixedDelay)라 한 작업 처리가 끝난 뒤 이 간격을 두고 다음 틱이 돈다(겹치지 않음).
 * - [staleRunningTimeout]: RUNNING이 이 시간을 넘기면 죽은 워커가 남긴 고아로 보고 FAILED로 회수한다
 *   (영원히 RUNNING 방지). 재시도하지 않는다(이중 비용 방지 — GenerationJob 주석).
 *
 * 기본값(2초·5분)은 "응답 지연을 너무 키우지 않으면서 폴링 비용은 낮은" MVP 적정선이다.
 */
@ConfigurationProperties(prefix = "resumaker.generation-job")
data class GenerationJobProperties(
    val pollIntervalMs: Long = 2000,
    val staleRunningTimeout: Duration = Duration.ofMinutes(5),
)
