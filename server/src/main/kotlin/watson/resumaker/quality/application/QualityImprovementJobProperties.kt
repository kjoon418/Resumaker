package watson.resumaker.quality.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 품질 개선 작업 워커 수치 외부 설정([watson.resumaker.generation.application.GenerationJobProperties] 동일 패턴).
 *
 * - [pollIntervalMs]: 워커 폴링 주기(ms). 매 틱마다 고아 RUNNING 회수 + 가장 오래된 PENDING 1건 처리.
 * - [staleRunningTimeout]: RUNNING이 이 시간을 넘기면 죽은 워커가 남긴 고아로 보고 FAILED로 회수한다
 *   (영원히 RUNNING 방지 — QC6 시간 초과 시 실패 수렴). 재시도하지 않는다(이중 비용 방지).
 *
 * 기본값(2초·5분)은 생성 작업 워커와 같은 MVP 적정선이다.
 */
@ConfigurationProperties(prefix = "resumaker.quality-improvement-job")
data class QualityImprovementJobProperties(
    val pollIntervalMs: Long = 2000,
    val staleRunningTimeout: Duration = Duration.ofMinutes(5),
)
