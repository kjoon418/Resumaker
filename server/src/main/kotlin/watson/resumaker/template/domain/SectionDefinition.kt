package watson.resumaker.template.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import watson.resumaker.common.domain.DomainValidationException

/**
 * 이력서 양식을 이루는 한 칸의 규격(도메인 이해 §2.5 "양식의 구성요소").
 * 다중 필드 VO이므로 @Embeddable(ResumeTemplate의 @ElementCollection 원소로 순서 보존 저장).
 *
 * **JPA 함정 회피(SkillTagConverter 주석 참고):** value class를 @ElementCollection 원소의 필드로 두면
 * Hibernate가 JdbcType을 추론하지 못한다(JdbcTypeRecommendationException). 따라서 이 @Embeddable의
 * 필드는 원시 타입(name: String, required: Boolean) + enum(@Enumerated STRING)으로 두고,
 * 도메인 불변식(name 공백 금지)은 init 블록에서 검증한다.
 *
 * 불변식: 섹션 이름은 비어 있을 수 없다(도메인 이해 §2.5 "각 섹션 name 비어있을 수 없음").
 */
@Embeddable
class SectionDefinition private constructor(
    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "character", nullable = false)
    val character: SectionCharacter,
    @Column(name = "required", nullable = false)
    val required: Boolean,
) {

    init {
        if (name.isBlank()) {
            throw DomainValidationException("섹션 이름을 적어 주세요. 예: 핵심 역량")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw DomainValidationException("섹션 이름은 ${MAX_NAME_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100

        fun of(name: String, character: SectionCharacter, required: Boolean): SectionDefinition =
            SectionDefinition(name = name, character = character, required = required)
    }
}
