package watson.resumaker.quality.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobId
import watson.resumaker.quality.domain.QualityImprovementJobStatus
import java.time.Instant
import java.util.UUID

/**
 * 품질 개선 작업 영속성([watson.resumaker.generation.infrastructure.GenerationJobRepository] 동형). 소유 조회는
 * ownerId를 조건에 포함해 소유 격리를 강제한다(QC8). 워커의 큐 픽업·고아 회수·원자 클레임은 ownerId 무관(시스템 작업).
 *
 * 식별자 타입은 UUID다(QualityImprovementJobId value class @Id → 내부 UUID 등록).
 */
interface QualityImprovementJobRepository : JpaRepository<QualityImprovementJob, UUID> {

    fun findByIdAndOwnerId(id: QualityImprovementJobId, ownerId: UserId): QualityImprovementJob?

    /** 가장 오래된 대기 작업 픽업용(FIFO). 워커가 한 틱에 1건 처리한다. */
    fun findFirstByStatusOrderByCreatedAtAsc(status: QualityImprovementJobStatus): QualityImprovementJob?

    /** 고아 RUNNING 회수용: 지정 시각보다 먼저 시작돼 아직 RUNNING인(=죽은 워커가 남긴) 작업을 모은다. */
    fun findByStatusAndStartedAtBefore(status: QualityImprovementJobStatus, cutoff: Instant): List<QualityImprovementJob>

    fun deleteByOwnerId(ownerId: UserId)

    /**
     * 대기 작업을 원자적으로 클레임한다. PENDING인 경우에만 RUNNING으로 바꾸고 시작 시각·시도 횟수를 올린다.
     * 반환 1이면 **이 호출이 작업을 소유**한다(여러 워커/틱이 경합해도 한 호출만 1을 받는다). 0이면 처리하지 않는다.
     */
    @Transactional
    @Modifying
    @Query(
        "update QualityImprovementJob j " +
            "set j.status = watson.resumaker.quality.domain.QualityImprovementJobStatus.RUNNING, " +
            "j.startedAt = :now, j.attempts = j.attempts + 1 " +
            "where j.id = :id and j.status = watson.resumaker.quality.domain.QualityImprovementJobStatus.PENDING",
    )
    fun claim(@Param("id") id: QualityImprovementJobId, @Param("now") now: Instant): Int
}
