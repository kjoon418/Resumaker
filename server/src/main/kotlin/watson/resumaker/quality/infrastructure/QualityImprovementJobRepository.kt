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

    /**
     * 한 산출물의 **가장 최근** 품질 개선 작업(소유 격리). 산출물 열람 화면이 비차단 진행 카드를 복원할 때 쓴다
     * (재진입 견고함 — 화면이 VM 상태에 기대지 않고 서버 권위로 최신 작업을 찾는다). 채택 시 작업을 삭제하므로,
     * 채택 완료된 작업은 더 이상 최신으로 잡히지 않는다(카드가 다시 뜨지 않음).
     */
    fun findFirstByArtifactIdAndOwnerIdOrderByCreatedAtDesc(artifactId: UUID, ownerId: UserId): QualityImprovementJob?

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
