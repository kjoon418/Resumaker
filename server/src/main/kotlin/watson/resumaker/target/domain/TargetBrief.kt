package watson.resumaker.target.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.IdentifierGenerator

/**
 * 목표 정보 애그리거트 루트(구현 설계 §3.4).
 *
 * 불변식: 채용 방향 텍스트는 비어 있을 수 없다(RecruitDirection VO가 검증). 회사명·직무명은 선택.
 * 소유 격리: ownerId로 사용자에 귀속된다. 저장·재사용 가능(목록 조회).
 *
 * 주생성자 private, 신규 작성은 create(), DB 복원은 retrieve()로 분리한다(검증 가이드).
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
) {

    /**
     * 목표 정보를 수정한다. 모든 값은 VO로 받는다.
     */
    fun update(recruitDirection: RecruitDirection, company: CompanyName?, job: JobTitle?) {
        this.recruitDirection = recruitDirection
        this.company = company
        this.job = job
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
        )

        fun retrieve(
            id: TargetBriefId,
            ownerId: UserId,
            recruitDirection: RecruitDirection,
            company: CompanyName?,
            job: JobTitle?,
        ): TargetBrief = TargetBrief(id, ownerId, recruitDirection, company, job)
    }
}
