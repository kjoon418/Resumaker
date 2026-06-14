package watson.resumaker.experience.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.IdentifierGenerator

/**
 * 경험 기록 애그리거트 루트(구현 설계 §3.3).
 *
 * 불변식: 제목·유형·본문은 비어 있을 수 없다(각 VO가 검증). 선택값(detail)은 비어도 유효하다.
 * 소유 격리: ownerId로 사용자에 귀속된다.
 *
 * 주생성자 private, 신규 작성은 create(), DB 복원은 retrieve()로 분리한다(검증 가이드).
 */
@Entity
@Table(name = "experience_records")
class ExperienceRecord private constructor(
    @Id
    val id: ExperienceRecordId,
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    @Column(name = "title", nullable = false)
    var title: ExperienceTitle,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: ExperienceType,
    @Column(name = "body", nullable = false, length = ExperienceBody.MAX_LENGTH)
    var body: ExperienceBody,
    @Embedded
    var detail: ExperienceDetail,
) {

    /**
     * 경험 기록을 점진적으로 보강·수정한다(기능 1 점진 보강).
     * 모든 값은 VO로 받아 불변식을 보장한다.
     */
    fun update(
        title: ExperienceTitle,
        type: ExperienceType,
        body: ExperienceBody,
        detail: ExperienceDetail,
    ) {
        this.title = title
        this.type = type
        this.body = body
        this.detail = detail
    }

    companion object {
        fun create(
            ownerId: UserId,
            title: ExperienceTitle,
            type: ExperienceType,
            body: ExperienceBody,
            detail: ExperienceDetail,
        ): ExperienceRecord = ExperienceRecord(
            id = ExperienceRecordId(IdentifierGenerator.newId()),
            ownerId = ownerId,
            title = title,
            type = type,
            body = body,
            detail = detail,
        )

        fun retrieve(
            id: ExperienceRecordId,
            ownerId: UserId,
            title: ExperienceTitle,
            type: ExperienceType,
            body: ExperienceBody,
            detail: ExperienceDetail,
        ): ExperienceRecord = ExperienceRecord(id, ownerId, title, type, body, detail)
    }
}
