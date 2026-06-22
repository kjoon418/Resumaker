package watson.resumaker.generation.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobId
import watson.resumaker.generation.domain.GenerationJobStatus
import java.time.Instant
import java.util.UUID

/**
 * 생성 작업 영속성. 소유 조회는 ownerId를 조건에 포함해 소유 격리를 강제한다(구현 설계 §4·§194). 워커가 쓰는
 * 큐 픽업·고아 회수·원자 클레임 쿼리는 ownerId 무관(시스템 작업)이다.
 *
 * 식별자 타입은 UUID다(GenerationJobId value class @Id → 내부 UUID 등록). 소유 격리 조회는 파생 쿼리로 VO를
 * 그대로 받는다. [deleteByOwnerId]는 계정 삭제 시 귀속 데이터 정리용이다(자식 experienceIds는 cascade 삭제).
 */
interface GenerationJobRepository : JpaRepository<GenerationJob, UUID> {

    fun findByIdAndOwnerId(id: GenerationJobId, ownerId: UserId): GenerationJob?

    fun findAllByOwnerIdOrderByCreatedAtDesc(ownerId: UserId): List<GenerationJob>

    /** 가장 오래된 대기 작업 픽업용(FIFO). 워커가 한 틱에 1건 처리한다. */
    fun findFirstByStatusOrderByCreatedAtAsc(status: GenerationJobStatus): GenerationJob?

    /** 고아 RUNNING 회수용: 지정 시각보다 먼저 시작돼 아직 RUNNING인(=죽은 워커가 남긴) 작업을 모은다. */
    fun findByStatusAndStartedAtBefore(status: GenerationJobStatus, cutoff: Instant): List<GenerationJob>

    fun deleteByOwnerId(ownerId: UserId)

    /**
     * 대기 작업을 원자적으로 클레임한다. PENDING인 경우에만 RUNNING으로 바꾸고 시작 시각·시도 횟수를 올린다.
     * 반환 1이면 **이 호출이 작업을 소유**한다(여러 워커/틱이 경합해도 한 호출만 1을 받는다 — 단일 동시성 보장).
     * 0이면 이미 다른 호출이 클레임했거나 상태가 바뀌었으니 처리하지 않는다.
     */
    @Transactional
    @Modifying
    @Query(
        "update GenerationJob j " +
            "set j.status = watson.resumaker.generation.domain.GenerationJobStatus.RUNNING, " +
            "j.startedAt = :now, j.attempts = j.attempts + 1 " +
            "where j.id = :id and j.status = watson.resumaker.generation.domain.GenerationJobStatus.PENDING",
    )
    fun claim(@Param("id") id: GenerationJobId, @Param("now") now: Instant): Int
}
