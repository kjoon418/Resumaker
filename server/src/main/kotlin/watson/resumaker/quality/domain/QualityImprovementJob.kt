package watson.resumaker.quality.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.IdentifierGenerator
import java.time.Instant
import java.util.UUID

/**
 * 타입 안전 품질 개선 작업 식별자. value class로 두고 Hibernate가 내부 타입(UUID)으로 네이티브 매핑한다
 * (레포지토리는 JpaRepository<_, UUID> — GenerationJob 패턴 동형).
 */
@JvmInline
value class QualityImprovementJobId(val value: UUID)

/**
 * 품질 개선 작업 상태(GenerationJobStatus 동형). PENDING → 워커 클레임 → RUNNING → 종료 SUCCEEDED|FAILED.
 */
enum class QualityImprovementJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    fun isActive(): Boolean = this == PENDING || this == RUNNING

    fun isTerminal(): Boolean = !isActive()
}

/**
 * 품질 개선 작업 애그리거트(품질 개선 기획 §3.1 "품질 개선 작업", 개발팀장 계약). 기존 [watson.resumaker.generation.domain.GenerationJob]과
 * **동형**으로, 사용자가 "이대로 다듬기"를 요청한 한 건의 비동기 처치 작업이다.
 *
 * **자동 적용(AUTO_REWRITE) 소견만 입력**으로 받아([findingIds]) 항목 후보([QualityCandidate])를 생성한다(개선 제안은
 * 진단 단계 산출물이라 이 작업을 거치지 않는다). 워커가 가장 오래된 PENDING을 원자 클레임해 RUNNING으로 바꾼 뒤
 * 처치 어댑터(외부 LLM)를 호출하고, 신뢰성 검증 + 원본 사실 토큰 보존 검증(QC3·QC4)을 통과한 후보만 영속한다.
 *
 * **재시도 금지(이중 비용 방지):** 한 작업은 한 번만 처리한다. 워커가 죽거나 너무 오래 RUNNING이면 recoverStale이
 * FAILED로 종료시키되 자동 재시도하지 않는다(검증실패의 항목 단위 자동 1회 재시도는 처치 프로세서 내부의 별개 규칙).
 *
 * **버전 고정:** 접수 시점의 활성 버전([versionId])을 작업에 고정 보관한다. 후보는 그 버전의 항목 텍스트를 입력으로
 * 만들어지므로, 채택 시 활성 버전이 바뀌어 있으면 도메인 채택 단계가 항목 부재로 거부한다(동시성 범위 밖 케이스).
 *
 * 주생성자 private, 신규는 [create]로 만든다(검증 가이드, GenerationJob 동형).
 */
@Entity
@Table(name = "quality_improvement_jobs")
class QualityImprovementJob private constructor(
    @Id
    @Column(name = "id")
    val id: QualityImprovementJobId,
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    @Column(name = "artifact_id", nullable = false)
    val artifactId: UUID,
    @Column(name = "version_id", nullable = false)
    val versionId: UUID,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "quality_improvement_job_finding_ids",
        joinColumns = [JoinColumn(name = "quality_improvement_job_id")],
    )
    @OrderColumn(name = "finding_id_order")
    @Column(name = "finding_id", nullable = false, length = 255)
    val findingIds: MutableList<String>,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: QualityImprovementJobStatus,
    @Column(name = "error_code", length = 255)
    var errorCode: String?,
    @Column(name = "error_message", length = 2000)
    var errorMessage: String?,
    @Column(name = "attempts", nullable = false)
    var attempts: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "started_at")
    var startedAt: Instant?,
    @Column(name = "finished_at")
    var finishedAt: Instant?,
) {

    /**
     * 처치 성공 종료. 워커가 채택 가능한 후보(≥1)를 영속한 뒤 호출한다. 후보는 별도 테이블([QualityCandidate])에
     * jobId로 연결되며(작업 애그리거트가 직접 소유하지 않음 — 조회 단계에서 jobId로 적재), 여기서는 상태만 확정한다.
     */
    fun markSucceeded(now: Instant) {
        this.status = QualityImprovementJobStatus.SUCCEEDED
        this.finishedAt = now
    }

    /**
     * 처치 실패 종료. 채택 가능한 후보가 0건(전 항목 검증 실패·전 항목 생성 실패)이거나 CLI 비가용·예기치 못한 실패일
     * 때 코드·메시지와 함께 호출한다. 자동 재시도하지 않는다(이중 비용 방지).
     */
    fun markFailed(code: String, message: String, now: Instant) {
        this.status = QualityImprovementJobStatus.FAILED
        this.errorCode = code
        this.errorMessage = message
        this.finishedAt = now
    }

    companion object {
        /**
         * 접수 시점에 PENDING 작업을 만든다(attempts=0). 워커가 픽업하기 전까지 대기열에 머문다.
         *
         * @param findingIds 처치할 AUTO_REWRITE 소견 식별자(진단 회차의 결정적 파생값). 워커가 산출물을 다시 적재해
         *   같은 항목·기준을 복원한다(소견 자체는 휘발이므로 식별자만 보관).
         */
        fun create(
            ownerId: UserId,
            artifactId: UUID,
            versionId: UUID,
            findingIds: List<String>,
            createdAt: Instant,
        ): QualityImprovementJob = QualityImprovementJob(
            id = QualityImprovementJobId(IdentifierGenerator.newId()),
            ownerId = ownerId,
            artifactId = artifactId,
            versionId = versionId,
            findingIds = findingIds.toMutableList(),
            status = QualityImprovementJobStatus.PENDING,
            errorCode = null,
            errorMessage = null,
            attempts = 0,
            createdAt = createdAt,
            startedAt = null,
            finishedAt = null,
        )
    }
}
