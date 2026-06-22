package watson.resumaker.target.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.IdentifierGenerator
import java.time.Instant

/**
 * 목표 정보 애그리거트 루트(구현 설계 §3.4).
 *
 * 불변식: 채용 방향 텍스트는 비어 있을 수 없다(RecruitDirection VO가 검증). 회사명·직무명은 선택.
 * 소유 격리: ownerId로 사용자에 귀속된다. 저장·재사용 가능(목록 조회).
 *
 * 주생성자 private, 신규 작성은 create(), DB 복원은 retrieve()로 분리한다(검증 가이드).
 *
 * **AI 작성 전략(파생값):** 채용 방향에서 LLM이 비동기로 추출한 작성 전략을 목표 내부에 보관한다. 전략 객체는
 * 도메인 VO [WritingStrategy]이나 엔티티는 그 JSON 직렬화 문자열([writingStrategyJson])만 들고, 직렬화/역직렬화는
 * application/presentation 계층이 Jackson으로 한다(JPA 컨버터 회피). [strategyStatus]가 추출 진행 상태를 나타낸다.
 * 채용 방향이 바뀌면 전략을 무효화하고 PENDING으로 되돌려 자동 재추출시킨다(company/job만 바뀌면 상태 불변).
 */
@Entity
@Table(name = "target_briefs")
class TargetBrief private constructor(
    @Id
    val id: TargetBriefId,
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    @Column(name = "recruit_direction", nullable = false, length = RecruitDirection.MAX_LENGTH)
    var recruitDirection: RecruitDirection,
    @Column(name = "company_name")
    var company: CompanyName?,
    @Column(name = "job_title")
    var job: JobTitle?,
    @Column(name = "writing_strategy", columnDefinition = "text")
    var writingStrategyJson: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_status", nullable = false)
    var strategyStatus: StrategyStatus,
    @Column(name = "strategy_extraction_started_at")
    var strategyExtractionStartedAt: Instant?,
) {

    /**
     * 목표 정보를 수정한다. 모든 값은 VO로 받는다.
     *
     * 채용 방향이 바뀌면 기존 전략을 무효화하고([writingStrategyJson]=null) 상태를 PENDING으로 되돌려 워커가
     * 자동 재추출하게 한다(추출 중이었다면 다음 틱 결과 쓰기가 0행이 되어 폐기된다). 회사·직무만 바뀌면 상태는
     * 그대로 둔다(전략은 채용 방향 본문에서 도출되므로 회사/직무 변경에는 영향받지 않는다).
     */
    fun update(recruitDirection: RecruitDirection, company: CompanyName?, job: JobTitle?) {
        if (this.recruitDirection != recruitDirection) {
            this.writingStrategyJson = null
            this.strategyStatus = StrategyStatus.PENDING
            this.strategyExtractionStartedAt = null
        }
        this.recruitDirection = recruitDirection
        this.company = company
        this.job = job
    }

    /**
     * 전략 추출을 다시 대기열에 올린다(상태 PENDING으로 리셋, 기존 전략 무효화). 사용자가 재시도하거나, 진행 중인
     * 추출을 무효화할 때 쓴다(멱등 — 이미 PENDING이어도 안전).
     */
    fun resetStrategyPending() {
        this.writingStrategyJson = null
        this.strategyStatus = StrategyStatus.PENDING
        this.strategyExtractionStartedAt = null
    }

    companion object {
        fun create(
            ownerId: UserId,
            recruitDirection: RecruitDirection,
            company: CompanyName?,
            job: JobTitle?,
        ): TargetBrief = TargetBrief(
            id = TargetBriefId(IdentifierGenerator.newId()),
            ownerId = ownerId,
            recruitDirection = recruitDirection,
            company = company,
            job = job,
            // 신규 목표는 전략 미추출 상태로 둔다(워커가 픽업해 추출).
            writingStrategyJson = null,
            strategyStatus = StrategyStatus.PENDING,
            strategyExtractionStartedAt = null,
        )

        fun retrieve(
            id: TargetBriefId,
            ownerId: UserId,
            recruitDirection: RecruitDirection,
            company: CompanyName?,
            job: JobTitle?,
            writingStrategyJson: String? = null,
            strategyStatus: StrategyStatus = StrategyStatus.PENDING,
            strategyExtractionStartedAt: Instant? = null,
        ): TargetBrief = TargetBrief(
            id, ownerId, recruitDirection, company, job,
            writingStrategyJson, strategyStatus, strategyExtractionStartedAt,
        )
    }
}
