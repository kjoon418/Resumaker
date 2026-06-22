package watson.resumaker.target.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.target.domain.StrategyStatus
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import java.time.Instant
import java.util.UUID

/**
 * 목표 정보 영속성. 모든 소유 조회는 ownerId를 조건에 포함해 소유 격리를 강제한다(구현 설계 §4).
 *
 * 식별자 타입은 UUID다(TargetBriefId value class @Id → 내부 UUID 등록). 소유 격리 조회는
 * 파생 쿼리(findByIdAndOwnerId 등)로 VO를 그대로 받는다.
 *
 * **작성 전략 워커용(시스템 작업, ownerId 무관):** 작성 전략 추출 워커가 쓰는 PENDING 픽업·고아 EXTRACTING 회수·
 * 원자 claim/결과 쓰기/실패 쓰기는 [GenerationJobRepository]의 큐 처리 패턴과 동형이다. 조건부 @Modifying 쓰기는
 * 반환 행 수로 소유/경합을 판정한다(상세는 각 메서드 주석).
 */
interface TargetBriefRepository : JpaRepository<TargetBrief, UUID> {

    fun findByIdAndOwnerId(id: TargetBriefId, ownerId: UserId): TargetBrief?

    fun findAllByOwnerId(ownerId: UserId): List<TargetBrief>

    fun deleteByOwnerId(ownerId: UserId)

    /** 가장 오래 기다린(=id 순) 전략 미추출 목표 1건 픽업용. 워커가 한 틱에 1건 처리한다. */
    fun findFirstByStrategyStatusOrderById(status: StrategyStatus): TargetBrief?

    /**
     * 작성 전략 추출 작업을 원자적으로 클레임한다. PENDING인 경우에만 EXTRACTING으로 바꾼다. 반환 1이면 **이 호출이
     * 작업을 소유**한다(여러 워커/틱이 경합해도 한 호출만 1을 받는다). 0이면 이미 다른 호출이 클레임했거나 상태가
     * 바뀌었으니 처리하지 않는다.
     */
    @Transactional
    @Modifying
    @Query(
        "update TargetBrief t " +
            "set t.strategyStatus = watson.resumaker.target.domain.StrategyStatus.EXTRACTING, " +
            "t.strategyExtractionStartedAt = :now " +
            "where t.id = :id and t.strategyStatus = watson.resumaker.target.domain.StrategyStatus.PENDING",
    )
    fun claimStrategyExtraction(@Param("id") id: TargetBriefId, @Param("now") now: Instant): Int

    /**
     * 추출 결과를 조건부로 쓴다. **EXTRACTING일 때만** 전략 JSON을 저장하고 READY로 전이한다. 반환 0이면 추출 중
     * 사용자가 채용 방향을 수정해 상태가 PENDING으로 돌아간 것이므로(claim했던 작업의 소유가 풀림), 호출자는 이
     * 결과를 폐기하고 다음 틱이 재추출하게 둔다.
     */
    @Transactional
    @Modifying
    @Query(
        "update TargetBrief t " +
            "set t.writingStrategyJson = :json, " +
            "t.strategyStatus = watson.resumaker.target.domain.StrategyStatus.READY " +
            "where t.id = :id and t.strategyStatus = watson.resumaker.target.domain.StrategyStatus.EXTRACTING",
    )
    fun writeStrategyResult(@Param("id") id: TargetBriefId, @Param("json") json: String): Int

    /**
     * 추출 실패를 조건부로 쓴다. **EXTRACTING일 때만** FAILED로 전이한다. 0이면 그 사이 재수정(PENDING)된 것이므로
     * 실패 표시를 폐기한다(다음 틱 재추출).
     */
    @Transactional
    @Modifying
    @Query(
        "update TargetBrief t " +
            "set t.strategyStatus = watson.resumaker.target.domain.StrategyStatus.FAILED " +
            "where t.id = :id and t.strategyStatus = watson.resumaker.target.domain.StrategyStatus.EXTRACTING",
    )
    fun markStrategyFailed(@Param("id") id: TargetBriefId): Int

    /**
     * 고아 EXTRACTING 회수용: 지정 시각보다 먼저 추출을 시작해 아직 EXTRACTING인(=죽은 워커가 남긴) 목표를 모은다.
     * 워커가 처리 도중 죽으면 목표가 영원히 EXTRACTING으로 남으므로, 시간 초과를 죽음으로 간주해 FAILED 처리한다
     * ([GenerationJobRepository.findByStatusAndStartedAtBefore]와 동형).
     */
    fun findByStrategyStatusAndStrategyExtractionStartedAtBefore(
        status: StrategyStatus,
        cutoff: Instant,
    ): List<TargetBrief>
}
