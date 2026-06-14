package watson.resumaker.experience.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.JoinColumn
import watson.resumaker.experience.infrastructure.SkillTagConverter

/**
 * 경험 기록의 선택 항목 묶음(상황/행동/결과 + 기간 + 사용 역량). 다중 필드 VO이므로 @Embeddable.
 * 모든 값이 비어도 유효하다(구현 설계 §3.3). 개별 값의 불변식은 각 VO가 검증한다.
 */
@Embeddable
class ExperienceDetail private constructor(
    @Column(name = "situation", length = 2000)
    val situation: String?,
    @Column(name = "action", length = 2000)
    val action: String?,
    @Column(name = "result", length = 2000)
    val result: String?,
    @Embedded
    val period: Period?,
    @ElementCollection
    @CollectionTable(
        name = "experience_skill_tags",
        joinColumns = [JoinColumn(name = "experience_record_id")],
    )
    @Column(name = "skill_tag", nullable = false)
    @Convert(converter = SkillTagConverter::class)
    val skillTags: List<SkillTag>,
) {

    companion object {
        val EMPTY: ExperienceDetail = ExperienceDetail(null, null, null, null, emptyList())

        fun of(
            situation: String?,
            action: String?,
            result: String?,
            period: Period?,
            skillTags: List<SkillTag>,
        ): ExperienceDetail = ExperienceDetail(
            situation = situation?.takeIf { it.isNotBlank() },
            action = action?.takeIf { it.isNotBlank() },
            result = result?.takeIf { it.isNotBlank() },
            period = period,
            skillTags = skillTags,
        )
    }
}
