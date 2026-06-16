package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.SectionId
import java.util.concurrent.ConcurrentHashMap

/**
 * 항목 단위 동시 재생성 거절을 위한 진행 중 생성 표시(수용 기준 20, 도메인 이해 §374, 구현 설계 §185).
 *
 * **동시성 메커니즘 선택(트레이드오프):**
 * - (택1) 인메모리 락(`ConcurrentHashMap`의 키 점유): 단일 인스턴스 MVP에서 가장 단순하다. 스키마·트랜잭션
 *   변경이 없고, 같은 항목 중복 요청을 즉시 거절한다. 한계: 다중 인스턴스로 수평 확장하면 인스턴스 간 공유가
 *   안 된다(프로세스 크래시 시 자동 해제됨 — 영구 점유 위험 없음).
 * - (대안) DB 비관/낙관 락: 다중 인스턴스에서도 정확하지만, 버전/항목에 잠금 컬럼·재시도 흐름을 더해야 하고
 *   짧은 외부 호출 구간 동안 DB 커넥션·락을 잡고 있어야 해 트랜잭션 분리 가이드(외부 호출은 tx 밖)와 충돌한다.
 * - (대안) 영속 상태 플래그(GENERATING 컬럼): 재생성 가시성은 좋으나, 크래시 시 플래그가 남아 수동/배치 정리가
 *   필요하고 이번 증분 범위(API 노출)를 넘는 스키마·정리 작업을 요구한다.
 *
 * **선택: 인메모리 락.** 근거 — MVP는 단일 인스턴스이고(도메인 이해 동시성 결정은 "거절+안내"만 요구),
 * 재생성의 임계 구간은 외부 호출을 포함해 tx 밖에서 길게 이어지므로 DB 락은 트랜잭션 분리 원칙과 충돌한다.
 * 크래시 시 프로세스 종료로 점유가 자동 소멸하므로 영구 점유 위험이 없다. 다중 인스턴스 확장 시 분산 락(Redis 등)으로
 * 이 seam을 교체한다.
 * **MVP 수용 위험:** 스레드 레벨 비정상 종료(finally 우회 — 예: ThreadDeath, 데몬 스레드 강제 중단)가 발생하면
 * 점유가 해제되지 않은 채 인-프로세스에 남는다. TTL·만료 메커니즘이 없으므로 해당 항목은 JVM 재기동 전까지 영구
 * 잠금 상태가 된다. finally를 항상 실행하는 정상 예외 경로(서비스 레이어 try-finally)가 이 위험을 완화하지만
 * 강제 종료 경로까지 보장하지는 않는다(분산 락·TTL 도입 시 이 seam을 교체한다).
 *
 * 서로 다른 항목은 서로 다른 키이므로 병렬 재생성이 허용된다(키 단위 점유).
 */
@Component
class SectionRegenerationLocks {

    private val inProgress: MutableSet<SectionId> = ConcurrentHashMap.newKeySet()

    /**
     * 주어진 항목의 재생성 점유를 시도한다. 이미 진행 중이면 false(중복 요청 → 거절). 점유에 성공하면 true.
     * 점유한 호출자는 작업 종료 시 반드시 [release]로 해제해야 한다.
     */
    fun tryAcquire(sectionId: SectionId): Boolean = inProgress.add(sectionId)

    /** 항목 재생성 점유를 해제한다(성공·실패·예외 무관 — finally에서 호출). */
    fun release(sectionId: SectionId) {
        inProgress.remove(sectionId)
    }
}
