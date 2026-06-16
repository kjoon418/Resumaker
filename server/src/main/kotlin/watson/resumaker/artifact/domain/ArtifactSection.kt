package watson.resumaker.artifact.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import watson.resumaker.artifact.infrastructure.ExperienceRecordIdConverter
import watson.resumaker.common.domain.IdentifierGenerator
import watson.resumaker.experience.domain.ExperienceRecordId

/**
 * 생성 항목(도메인 이해 §340·§380). 한 버전이 담는 산출물 내용 스냅샷의 한 칸이다.
 *
 * definitionKey — 버전 간 '같은 항목' 대응 키(수용 기준 22). 이력서는 TemplateSnapshot의 SnapshotSection
 * definitionKey를 그대로 가지며, 채택으로 새 버전을 만들 때 같은 키로 대응된다. 포트폴리오는 양식이 없어
 * 경험과 1:1이므로 경험 식별자를 키로 사용한다(생성 계층이 부여).
 *
 * 생성 근거 두 층위:
 * - 층위1: sourceExperienceIds(항목 출처 경험 목록, 경험과 N:M). @ElementCollection + 컨버터(소프트 참조).
 * - 층위2: factGroundings(수치/고유명사 1—1 근거). 애그리거트 내부 자식(@OneToMany cascade+orphanRemoval).
 *
 * **컨테이너 불변성:** sourceExperienceIdList는 재할당되지 않으므로 private val로 둔다. 항목 채택 시
 * content/status만 갱신되며(adoptSection), 출처 목록 컨테이너는 새 버전 복제 시점에 새 리스트로 격리된다.
 *
 * **양방향 매핑(구현 설계 best practice):** factGroundings는 자식이 FK를 소유(@ManyToOne)하고 부모는
 * mappedBy로 참조한다. 본 항목 자신도 Version의 자식이므로 version FK를 @ManyToOne으로 소유한다.
 * 자식 설정 시 역참조를 주입(assignSection/assignVersion)해 양방향 일관성을 유지한다.
 *
 * **스냅샷 격리:** sourceExperienceIds는 경험에 FK·cascade 없이 식별자 String만 저장한다(구현 설계 §164).
 *
 * Artifact 애그리거트 내부 엔티티. 주생성자 private, 신규는 create(), 복제는 copyForNewVersion()으로 분리.
 */
@Entity
@Table(name = "artifact_sections")
class ArtifactSection private constructor(
    @Id
    @Column(name = "id")
    val id: SectionId,
    @Column(name = "definition_key", nullable = false, length = MAX_KEY_LENGTH)
    val definitionKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", nullable = false)
    val sectionKind: SectionKind,
    @Embedded
    var content: SectionContent,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SectionStatus,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "artifact_section_source_experiences",
        joinColumns = [JoinColumn(name = "artifact_section_id")],
    )
    @OrderColumn(name = "source_order")
    @Column(name = "source_experience_id", nullable = false)
    @Convert(converter = ExperienceRecordIdConverter::class)
    private val sourceExperienceIdList: MutableList<ExperienceRecordId>,
    @OneToMany(
        mappedBy = "section",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @OrderColumn(name = "grounding_order")
    private val factGroundingList: MutableList<FactGrounding>,
) {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private var version: Version? = null

    init {
        factGroundingList.forEach { it.assignSection(this) }
    }

    /** 부모 Version이 자식 목록을 구성할 때 역참조를 주입한다(양방향 일관성). */
    internal fun assignVersion(owner: Version) {
        this.version = owner
    }

    /** 항목 출처 경험 목록(읽기 전용 뷰). */
    val sourceExperienceIds: List<ExperienceRecordId> get() = sourceExperienceIdList.toList()

    /** 고정밀 사실 근거 목록(읽기 전용 뷰). */
    val factGroundings: List<FactGrounding> get() = factGroundingList.toList()

    companion object {
        const val MAX_KEY_LENGTH = 100

        fun create(
            definitionKey: String,
            sectionKind: SectionKind,
            content: SectionContent,
            status: SectionStatus,
            sourceExperienceIds: List<ExperienceRecordId>,
            factGroundings: List<FactGrounding>,
        ): ArtifactSection = ArtifactSection(
            id = SectionId(IdentifierGenerator.newId()),
            definitionKey = definitionKey,
            sectionKind = sectionKind,
            content = content,
            status = status,
            sourceExperienceIdList = sourceExperienceIds.toMutableList(),
            factGroundingList = factGroundings.toMutableList(),
        )

        fun retrieve(
            id: SectionId,
            definitionKey: String,
            sectionKind: SectionKind,
            content: SectionContent,
            status: SectionStatus,
            sourceExperienceIds: List<ExperienceRecordId>,
            factGroundings: List<FactGrounding>,
        ): ArtifactSection = ArtifactSection(
            id = id,
            definitionKey = definitionKey,
            sectionKind = sectionKind,
            content = content,
            status = status,
            sourceExperienceIdList = sourceExperienceIds.toMutableList(),
            factGroundingList = factGroundings.toMutableList(),
        )

        /**
         * 새 버전을 만들 때 이 항목을 **그대로 복제**한다(수용 기준 19: 미변경 항목은 직전 버전에서 복제).
         * 새 SectionId를 발급하고 자식 근거도 새 행으로 복제해, 버전 간 행이 공유되지 않게 한다.
         * sourceExperienceIdList도 새 리스트로 복제해 버전 간 출처 목록 컨테이너가 공유되지 않는다.
         * definitionKey는 유지되어 버전 간 항목이 대응된다.
         */
        fun copyForNewVersion(source: ArtifactSection): ArtifactSection = ArtifactSection(
            id = SectionId(IdentifierGenerator.newId()),
            definitionKey = source.definitionKey,
            sectionKind = source.sectionKind,
            content = source.content,
            status = source.status,
            sourceExperienceIdList = source.sourceExperienceIdList.toMutableList(),
            factGroundingList = source.factGroundingList.map { FactGrounding.copyOf(it) }.toMutableList(),
        )
    }
}
