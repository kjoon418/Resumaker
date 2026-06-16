package watson.resumaker.artifact.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import watson.resumaker.common.domain.IdentifierGenerator
import java.time.Instant

/**
 * 버전(도메인 이해 §342). **산출물 단위의 스냅샷**으로, 그 시점 산출물을 이루는 모든 생성 항목의
 * 내용을 통째로 담는다. 부분 실패 버전(*_FAILED 항목 포함)도 정식 버전이다(수용 기준 9).
 *
 * Artifact 애그리거트 내부 엔티티. sections는 자식으로 cascade ALL+orphanRemoval되며 @OrderColumn으로
 * 순서를 보존한다. 한 버전이 만들어진 뒤 그 내용은 불변이며(채택은 항상 새 버전을 만든다),
 * createdAt 오름차순이 버전 생성 순서를 나타낸다(pruneOldest의 기준).
 *
 * **양방향 매핑(구현 설계 best practice):** sections는 자식 ArtifactSection이 FK를 소유(@ManyToOne)하고
 * 부모는 mappedBy로 참조한다. 본 버전 자신도 Artifact의 자식이므로 artifact FK를 @ManyToOne으로 소유한다.
 * 자식 설정 시 역참조를 주입(assignVersion/assignArtifact)해 잉여 UPDATE 없이 일관성을 유지한다.
 */
@Entity
@Table(name = "versions")
class Version private constructor(
    @Id
    @Column(name = "id")
    val id: VersionId,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @OneToMany(
        mappedBy = "version",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @OrderColumn(name = "section_order")
    private val sectionList: MutableList<ArtifactSection>,
) {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    private var artifact: Artifact? = null

    init {
        sectionList.forEach { it.assignVersion(this) }
    }

    /** 부모 Artifact가 버전 목록을 구성할 때 역참조를 주입한다(양방향 일관성). */
    internal fun assignArtifact(owner: Artifact) {
        this.artifact = owner
    }

    /** 이 버전이 담은 생성 항목 목록(읽기 전용 뷰, 순서 보존). */
    val sections: List<ArtifactSection> get() = sectionList.toList()

    /** 주어진 SectionId의 항목을 찾는다. */
    fun sectionById(sectionId: SectionId): ArtifactSection? =
        sectionList.firstOrNull { it.id == sectionId }

    companion object {
        fun create(sections: List<ArtifactSection>, createdAt: Instant): Version = Version(
            id = VersionId(IdentifierGenerator.newId()),
            createdAt = createdAt,
            sectionList = sections.toMutableList(),
        )

        fun retrieve(id: VersionId, createdAt: Instant, sections: List<ArtifactSection>): Version =
            Version(id, createdAt, sections.toMutableList())
    }
}
