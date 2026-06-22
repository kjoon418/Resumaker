package watson.resumaker.artifact.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import watson.resumaker.target.domain.RecruitDirection

/**
 * 산출물이 소유하는 목표 정보의 **불변 스냅샷**(도메인 이해 §347 "경험/원본이 바뀌거나 삭제돼도 산출물은 불변").
 *
 * 이력서·포트폴리오 둘 다 생성 시점의 목표(채용 방향·회사·직무)를 이 VO에 복제 소유한다.
 * 원본 [watson.resumaker.target.domain.TargetBrief]이 수정·삭제되어도 산출물의 목표 스냅샷은 변하지 않으므로
 * **항목 재생성이 항상 원본 목표 맥락을 그대로 사용**할 수 있다(§364: 목표 변경 = 새 산출물).
 *
 * 설계 결정 — 채용 방향의 도메인 불변식(비어 있지 않음·길이 상한)은 원본과 동일한 단일 값 객체
 * [RecruitDirection]이 소유한다. 스냅샷은 같은 도메인 제약을 별도로 재검증하지 않고, 팩토리 [of]가
 * [RecruitDirection]을 받게 해 **타입 경계에서 유효성을 보장**한다(중복 검증으로 인한 드리프트 방지 —
 * 과거 스냅샷이 원본보다 좁은 200자 한도를 따로 두어 5000자 목표로 생성이 막히던 버그, QA 2026-06-21 #1·#2).
 * artifact가 다른 도메인의 leaf 식별자([watson.resumaker.experience.domain.ExperienceRecordId])를 참조하는 것과
 * 동일하게, leaf 값 객체 [RecruitDirection]만 재사용한다(애그리거트 루트·구조는 여전히 격리).
 *
 * 영속: 컬럼은 단순 문자열로 저장한다(@Embeddable 인스턴스화·복원 단순성). 컬럼 폭은 원본 상한
 * ([RecruitDirection.MAX_LENGTH])과 일치시켜 잘림을 막는다.
 *
 * JPA 영속: Artifact 엔티티에 @Embedded로 삽입한다(컬럼 접두어 `target_`). kotlin-jpa 플러그인이 @Embeddable에
 * init을 우회하는 합성 no-arg 생성자를 생성하므로 별도 보조 생성자를 두지 않는다(SnapshotSection과 동형).
 */
@Embeddable
class ArtifactTargetSnapshot private constructor(
    @Column(name = "target_recruit_direction", nullable = false, length = RecruitDirection.MAX_LENGTH)
    val recruitDirection: String,
    @Column(name = "target_company_name")
    val company: String?,
    @Column(name = "target_job_title")
    val job: String?,
    /**
     * 생성 시점에 READY였던 AI 작성 전략의 JSON 직렬화 문자열(없으면 null — 원문으로 생성). 산출물은 생성에 쓴
     * 작성 관점도 함께 불변 보존한다(원본 목표가 바뀌어도 산출물 맥락은 그대로). 구조화 파싱은 상위 계층 책임이다
     * (엔티티는 JSON 문자열만 보관 — TargetBrief와 동일 정책).
     */
    @Column(name = "target_writing_strategy", columnDefinition = "text")
    val writingStrategyJson: String?,
) {

    companion object {
        /**
         * 검증된 채용 방향 VO로부터 스냅샷을 만든다. [RecruitDirection]은 생성 시 비어있음·길이 상한을 이미
         * 검증하므로, 스냅샷은 그 값을 그대로 복제만 한다(불변식은 VO가 단일 소유).
         *
         * @param writingStrategyJson 생성에 쓴 작성 전략 JSON(READY일 때만, 그 외 null).
         */
        fun of(
            recruitDirection: RecruitDirection,
            company: String?,
            job: String?,
            writingStrategyJson: String? = null,
        ): ArtifactTargetSnapshot = ArtifactTargetSnapshot(recruitDirection.value, company, job, writingStrategyJson)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArtifactTargetSnapshot) return false
        return recruitDirection == other.recruitDirection &&
            company == other.company &&
            job == other.job &&
            writingStrategyJson == other.writingStrategyJson
    }

    override fun hashCode(): Int {
        var result = recruitDirection.hashCode()
        result = 31 * result + (company?.hashCode() ?: 0)
        result = 31 * result + (job?.hashCode() ?: 0)
        result = 31 * result + (writingStrategyJson?.hashCode() ?: 0)
        return result
    }
}
