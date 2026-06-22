package watson.resumaker.generation.domain

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
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.common.domain.IdentifierGenerator
import java.time.Instant
import java.util.UUID

/**
 * 타입 안전 생성 작업 식별자(산출물 AI 생성의 비동기 작업 단위).
 *
 * value class로 둔다. Hibernate 6이 inline class를 내부 타입(UUID)으로 네이티브 매핑하며,
 * 식별자 타입은 UUID로 등록된다(레포지토리는 JpaRepository<_, UUID>).
 */
@JvmInline
value class GenerationJobId(val value: UUID)

/**
 * 생성 작업 상태. 제출 직후 PENDING → 워커가 클레임하면 RUNNING → 종료 시 SUCCEEDED|FAILED.
 *
 * [isActive]가 true면 아직 처리 중(삭제 불가), [isTerminal]이면 종료돼 결과(artifactId 또는 errorCode)가 확정됐다.
 */
enum class GenerationJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    fun isActive(): Boolean = this == PENDING || this == RUNNING

    fun isTerminal(): Boolean = !isActive()
}

/**
 * 산출물 AI 생성을 비동기(논블로킹)로 수행하기 위한 생성 작업 애그리거트.
 *
 * 동기 생성(20~60초 블로킹)을 작업 기반 비동기로 바꾼다: 제출 즉시 PENDING 작업을 만들어 jobId를 돌려주고
 * (202), 백그라운드 워커가 가장 오래된 PENDING을 원자 클레임해 RUNNING으로 바꾼 뒤 생성 파이프라인
 * ([watson.resumaker.generation.application.ArtifactGenerationService])을 호출한다. 클라이언트는 jobId로
 * 폴링해 완료(SUCCEEDED→artifactId)나 실패(FAILED→errorCode/message)를 확인한다.
 *
 * **카드 제목용 비정규화([targetCompany]):** 작업 목록 카드에 회사명을 보여주려고 제출 시점의 목표 회사명을
 * 작업에 복제 소유한다(목표가 나중에 바뀌어도 카드 표시는 제출 당시 맥락 유지). 실제 생성 맥락은 워커가
 * targetId로 다시 적재하므로 이 필드는 표시 전용이다.
 *
 * **재시도 금지(이중 비용 방지):** 한 작업은 한 번만 처리한다. 워커가 죽거나 작업이 너무 오래 RUNNING이면
 * recoverStale이 FAILED로 종료시키되 자동 재시도하지 않는다(생성 파이프라인이 가드레일을 차감하므로 재시도는
 * 이중 차감·이중 비용을 부른다 — 사용자가 직접 다시 만든다).
 *
 * 주생성자 private, 신규는 [create]로 만든다(검증 가이드, 기존 애그리거트와 동형).
 */
@Entity
@Table(name = "generation_jobs")
class GenerationJob private constructor(
    @Id
    @Column(name = "id")
    val id: GenerationJobId,
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    val kind: ArtifactKind,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "generation_job_experience_ids",
        joinColumns = [JoinColumn(name = "generation_job_id")],
    )
    @OrderColumn(name = "experience_id_order")
    @Column(name = "experience_id")
    val experienceIds: MutableList<UUID>,
    @Column(name = "target_id", nullable = false)
    val targetId: UUID,
    @Column(name = "template_id")
    val templateId: UUID?,
    @Column(name = "target_company", length = 255)
    val targetCompany: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: GenerationJobStatus,
    @Column(name = "artifact_id")
    var artifactId: UUID?,
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
     * 생성 성공 종료. 워커가 생성 파이프라인의 성공 응답을 받아 호출한다(최소 1항목 성공 — 부분 실패도 성공으로
     * 본다). 산출물 식별자와 종료 시각을 확정한다.
     */
    fun markSucceeded(artifactId: UUID, now: Instant) {
        this.status = GenerationJobStatus.SUCCEEDED
        this.artifactId = artifactId
        this.finishedAt = now
    }

    /**
     * 생성 실패 종료. 워커가 예외를 매핑한 에러 코드·메시지와 함께 호출한다(가드레일 초과·CLI 비가용·근거 0건
     * ·원본 삭제·기타). 자동 재시도하지 않는다(이중 비용 방지 — 클래스 주석).
     */
    fun markFailed(code: String, message: String, now: Instant) {
        this.status = GenerationJobStatus.FAILED
        this.errorCode = code
        this.errorMessage = message
        this.finishedAt = now
    }

    companion object {
        /**
         * 제출 시점에 PENDING 작업을 만든다(attempts=0). 워커가 픽업하기 전까지 대기열에 머문다.
         *
         * @param experienceIds 생성에 쓸 경험 식별자(원시 UUID — 작업은 VO를 모르고 워커가 다시 VO로 감싼다).
         * @param targetId       목표 식별자(워커가 소유 격리로 다시 적재해 생성 맥락을 만든다).
         * @param templateId     지정 양식 식별자(이력서 선택, 포트폴리오는 항상 null).
         * @param targetCompany  카드 제목용 비정규화 회사명(표시 전용, null 허용).
         */
        fun create(
            ownerId: UserId,
            kind: ArtifactKind,
            experienceIds: List<UUID>,
            targetId: UUID,
            templateId: UUID?,
            targetCompany: String?,
            createdAt: Instant,
        ): GenerationJob = GenerationJob(
            id = GenerationJobId(IdentifierGenerator.newId()),
            ownerId = ownerId,
            kind = kind,
            experienceIds = experienceIds.toMutableList(),
            targetId = targetId,
            templateId = templateId,
            targetCompany = targetCompany,
            status = GenerationJobStatus.PENDING,
            artifactId = null,
            errorCode = null,
            errorMessage = null,
            attempts = 0,
            createdAt = createdAt,
            startedAt = null,
            finishedAt = null,
        )
    }
}
