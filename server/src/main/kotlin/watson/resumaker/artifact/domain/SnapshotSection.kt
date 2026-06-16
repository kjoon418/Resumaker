package watson.resumaker.artifact.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import watson.resumaker.common.domain.DomainValidationException

/**
 * 양식 스냅샷을 이루는 한 섹션 정의의 **불변 복제본**(도메인 이해 §359 "양식과 생성 항목의 대응", 수용 기준 22).
 *
 * 설계 결정 — template 도메인의 SectionDefinition을 재사용하지 않고 artifact 전용 복제 VO로 둔다.
 * 근거: 산출물은 생성 시점 양식의 **불변 스냅샷**을 소유해야 하며(애그리거트 경계),
 * 원본 양식(ResumeTemplate)이 수정·삭제되어도 과거 산출물이 무너지지 않아야 한다(기능 7과 동형의 격리).
 * template.SectionDefinition을 직접 참조하면 양식 애그리거트에 결합되어 이 불변 보장이 깨진다.
 *
 * definitionKey — 버전 간 '같은 항목' 대응 키(도메인 이해 §363, 수용 기준 22).
 * 한 산출물의 모든 버전은 동일한 스냅샷을 공유하므로, 각 섹션 정의는 안정적인 키를 가진다.
 * ArtifactSection.definitionKey가 이 키를 참조해 버전 간 항목이 대응된다.
 *
 * **JPA 함정 회피(SkillTagConverter 주석):** @ElementCollection 원소의 필드는 원시 타입(String, Boolean)
 * + enum(@Enumerated STRING)으로 두어 JdbcType 추론 함정을 피한다. 불변식은 init에서 검증한다.
 */
@Embeddable
class SnapshotSection private constructor(
    @Column(name = "definition_key", nullable = false, length = MAX_KEY_LENGTH)
    val definitionKey: String,
    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", nullable = false)
    val sectionKind: SectionKind,
    @Column(name = "required", nullable = false)
    val required: Boolean,
) {

    init {
        if (definitionKey.isBlank()) {
            throw DomainValidationException("섹션 정의 키는 비어 있을 수 없어요.")
        }
        if (name.isBlank()) {
            throw DomainValidationException("섹션 이름을 적어 주세요.")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw DomainValidationException("섹션 이름은 ${MAX_NAME_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_KEY_LENGTH = 100
        const val MAX_NAME_LENGTH = 100

        fun of(
            definitionKey: String,
            name: String,
            sectionKind: SectionKind,
            required: Boolean,
        ): SnapshotSection = SnapshotSection(definitionKey, name, sectionKind, required)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotSection) return false
        return definitionKey == other.definitionKey &&
            name == other.name &&
            sectionKind == other.sectionKind &&
            required == other.required
    }

    override fun hashCode(): Int {
        var result = definitionKey.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + sectionKind.hashCode()
        result = 31 * result + required.hashCode()
        return result
    }
}
