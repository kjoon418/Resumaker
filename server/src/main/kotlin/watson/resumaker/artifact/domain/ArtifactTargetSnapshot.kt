package watson.resumaker.artifact.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import watson.resumaker.common.domain.DomainValidationException

/**
 * 산출물이 소유하는 목표 정보의 **불변 스냅샷**(도메인 이해 §347 "경험/원본이 바뀌거나 삭제돼도 산출물은 불변").
 *
 * 이력서·포트폴리오 둘 다 생성 시점의 목표(채용 방향·회사·직무)를 이 VO에 복제 소유한다.
 * 원본 [watson.resumaker.target.domain.TargetBrief]이 수정·삭제되어도 산출물의 목표 스냅샷은 변하지 않으므로
 * **항목 재생성이 항상 원본 목표 맥락을 그대로 사용**할 수 있다(§364: 목표 변경 = 새 산출물).
 *
 * 설계 결정 — target 도메인의 TargetBrief를 재사용하지 않고 artifact 전용 복제 VO로 둔다.
 * 근거: SnapshotSection이 template 도메인을 재사용하지 않는 것과 동일 철학(애그리거트 경계 격리).
 *
 * JPA 영속: Artifact 엔티티에 @Embedded로 삽입한다(컬럼 접두어 `target_`). 순수 도메인 VO이지만
 * JPA가 Embeddable 클래스를 직접 인스턴스화하므로 @Embeddable + 합성 no-arg 생성자를 허용한다.
 *
 * 불변식:
 * - recruitDirection은 비어 있을 수 없다(목표의 핵심 필드 — TargetBrief 불변식과 동형).
 */
/**
 * kotlin-jpa 플러그인이 @Embeddable 클래스에 대해 init 블록을 우회하는 합성 no-arg 생성자를 자동 생성한다.
 * 따라서 별도 no-arg 보조 생성자를 작성하지 않는다(보조 생성자가 `this(...)` 위임을 통해 init을 실행하면
 * 빈 recruitDirection이 DomainValidationException을 던져 Hibernate 복원이 실패한다 — SnapshotSection과 동형).
 */
@Embeddable
class ArtifactTargetSnapshot private constructor(
    @Column(name = "target_recruit_direction", nullable = false, length = MAX_DIRECTION_LENGTH)
    val recruitDirection: String,
    @Column(name = "target_company_name")
    val company: String?,
    @Column(name = "target_job_title")
    val job: String?,
) {

    init {
        if (recruitDirection.isBlank()) {
            throw DomainValidationException("목표의 채용 방향은 비어 있을 수 없어요.")
        }
        if (recruitDirection.length > MAX_DIRECTION_LENGTH) {
            throw DomainValidationException("채용 방향은 ${MAX_DIRECTION_LENGTH}자 이내로 입력해 주세요.")
        }
    }

    companion object {
        const val MAX_DIRECTION_LENGTH = 200

        fun of(
            recruitDirection: String,
            company: String?,
            job: String?,
        ): ArtifactTargetSnapshot = ArtifactTargetSnapshot(recruitDirection, company, job)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArtifactTargetSnapshot) return false
        return recruitDirection == other.recruitDirection &&
            company == other.company &&
            job == other.job
    }

    override fun hashCode(): Int {
        var result = recruitDirection.hashCode()
        result = 31 * result + (company?.hashCode() ?: 0)
        result = 31 * result + (job?.hashCode() ?: 0)
        return result
    }
}
