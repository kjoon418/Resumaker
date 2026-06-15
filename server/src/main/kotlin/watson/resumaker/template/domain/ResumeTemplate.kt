package watson.resumaker.template.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.IdentifierGenerator

/**
 * 이력서 양식 애그리거트 루트(도메인 이해 §2.5, FU-A).
 *
 * 양식은 목표 정보와 같은 격의 생성 입력이며 사용자 소유로 격리·저장된다(사용자 1 — N 양식).
 * 양식은 섹션 정의의 **순서 있는 목록**이며, 구조·형식만 담고 사실 내용은 담지 않는다.
 *
 * 불변식:
 * - name 비어있을 수 없음(TemplateName VO가 검증).
 * - sections는 최소 1개(빈 양식 불성립 — 도메인 이해 §2.5).
 * - 각 섹션 name 비어있을 수 없음(SectionDefinition init이 검증).
 *
 * 영속화: sections는 @ElementCollection + @OrderColumn으로 순서를 보존한다. SectionDefinition은
 * 원시 타입+enum 임베더블이라 JdbcType 추론 함정(SkillTagConverter 주석)을 피한다.
 *
 * 주생성자 private, 신규 작성은 create(), DB 복원은 retrieve()로 분리한다(검증 가이드).
 */
@Entity
@Table(name = "resume_templates")
class ResumeTemplate private constructor(
    @Id
    val id: ResumeTemplateId,
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    @Column(name = "name", nullable = false, length = TemplateName.MAX_LENGTH)
    var name: TemplateName,
    @ElementCollection
    @CollectionTable(
        name = "resume_template_sections",
        joinColumns = [JoinColumn(name = "resume_template_id")],
    )
    @OrderColumn(name = "section_order")
    private var sectionList: MutableList<SectionDefinition>,
) {

    /** 섹션 정의의 순서 있는 목록(읽기 전용 뷰). */
    val sections: List<SectionDefinition> get() = sectionList.toList()

    /**
     * 양식 이름·섹션을 수정한다. 모든 값은 VO로 받으며 불변식을 다시 검증한다.
     */
    fun update(name: TemplateName, sections: List<SectionDefinition>) {
        requireAtLeastOneSection(sections)
        this.name = name
        this.sectionList = sections.toMutableList()
    }

    companion object {
        fun create(
            ownerId: UserId,
            name: TemplateName,
            sections: List<SectionDefinition>,
        ): ResumeTemplate {
            requireAtLeastOneSection(sections)
            return ResumeTemplate(
                id = ResumeTemplateId(IdentifierGenerator.newId()),
                ownerId = ownerId,
                name = name,
                sectionList = sections.toMutableList(),
            )
        }

        fun retrieve(
            id: ResumeTemplateId,
            ownerId: UserId,
            name: TemplateName,
            sections: List<SectionDefinition>,
        ): ResumeTemplate = ResumeTemplate(id, ownerId, name, sections.toMutableList())

        private fun requireAtLeastOneSection(sections: List<SectionDefinition>) {
            if (sections.isEmpty()) {
                throw DomainValidationException("양식에는 섹션이 적어도 하나 필요해요. 담을 칸을 하나 이상 추가해 주세요.")
            }
        }
    }
}
