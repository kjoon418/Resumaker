package watson.resumaker.artifact.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.Transient
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.IdentifierGenerator
import java.time.Instant

/**
 * 산출물 애그리거트 루트(구현 설계 §3.5, 도메인 이해 §337~385).
 *
 * 산출물은 여러 버전(산출물 단위 스냅샷)을 가지며 항상 하나의 활성 버전을 가리킨다. 이력서는 생성 시점
 * 양식의 불변 스냅샷(templateSnapshot) 하나를 가지며 **모든 버전이 이를 공유**한다. 포트폴리오는 스냅샷이
 * 없다(kind == PORTFOLIO → templateSnapshot == null).
 *
 * 불변식(생성자/init에서 강제):
 * - versions는 비어 있을 수 없다(최소 1개 — 초기 버전).
 * - activeVersionId는 versions 안에 존재해야 한다(항상 유효한 활성 버전 — 도메인 이해 §343·§292).
 * - RESUME는 양식 스냅샷을 가지고, PORTFOLIO는 가지지 않는다(수용 기준 22).
 * - 섹션 종류는 산출물 종류와 정합해야 한다(RESUME→{SUMMARY,CAREER}, PORTFOLIO→{EXPERIENCE_NARRATIVE} — §166~169).
 *
 * **양식 스냅샷 소유(설계 결정):** 스냅샷은 Artifact가 @ElementCollection<SnapshotSection>으로 직접 소유한다.
 * template 도메인의 SectionDefinition을 재사용하지 않고 artifact 전용 복제 VO(SnapshotSection)로 둔다
 * (SnapshotSection 주석 참고). 원본 양식이 바뀌거나 삭제돼도 산출물은 불변이다. 포트폴리오/이력서 구분은
 * kind로 명시 판정하며, templateSnapshot은 init에서 1회 구성해 캐시한다(게터에서 재검증·throw 없음).
 *
 * 영속: versions는 자식으로 cascade ALL+orphanRemoval(@OrderColumn 순서 보존). 스냅샷은 @ElementCollection.
 */
@Entity
@Table(name = "artifacts")
class Artifact private constructor(
    @Id
    @Column(name = "id")
    val id: ArtifactId,
    @Column(name = "owner_id", nullable = false)
    val ownerId: UserId,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    val kind: ArtifactKind,
    @Embedded
    val targetSnapshot: ArtifactTargetSnapshot,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "artifact_template_snapshot_sections",
        joinColumns = [JoinColumn(name = "artifact_id")],
    )
    @OrderColumn(name = "snapshot_section_order")
    private val snapshotSectionList: MutableList<SnapshotSection>,
    @OneToMany(
        mappedBy = "artifact",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @OrderColumn(name = "version_order")
    private val versionList: MutableList<Version>,
    @Column(name = "active_version_id", nullable = false)
    private var activeVersionId: VersionId,
) {

    @Transient
    private var cachedTemplateSnapshot: TemplateSnapshot? = null

    @Transient
    private var templateSnapshotResolved: Boolean = false

    /**
     * 산출물이 소유한 양식 스냅샷(포트폴리오는 null). 모든 버전이 공유하는 불변 스냅샷.
     *
     * 게터가 매 접근마다 재검증·재생성·throw하지 않도록 **최초 접근 시 1회만** 구성해 캐시한다.
     * 포트폴리오/이력서 구분은 snapshotSectionList.isEmpty() 추론이 아니라 kind로 명시 판정한다.
     *
     * 수동 메모이즈로 두는 이유: Hibernate가 합성 no-arg 생성자로 인스턴스화하면 init/프로퍼티 이니셜라이저·
     * lazy 델리게이트가 모두 우회되어 컬렉션이 비어 있을 때 캐시되거나 델리게이트가 null이 된다. 게터 내부에서
     * 컬렉션이 채워진 뒤 첫 접근 시 계산·캐시하면 복원 경로에서 안전하다(이후 접근은 재검증 없음).
     */
    val templateSnapshot: TemplateSnapshot?
        @Transient
        get() {
            if (!templateSnapshotResolved) {
                cachedTemplateSnapshot =
                    if (kind == ArtifactKind.RESUME) TemplateSnapshot.of(snapshotSectionList.toList()) else null
                templateSnapshotResolved = true
            }
            return cachedTemplateSnapshot
        }

    init {
        if (versionList.isEmpty()) {
            throw DomainValidationException("산출물은 버전이 적어도 하나 필요해요.")
        }
        if (versionList.none { it.id == activeVersionId }) {
            throw DomainValidationException("활성 버전은 산출물의 버전 중 하나여야 해요.")
        }
        if (kind == ArtifactKind.RESUME && snapshotSectionList.isEmpty()) {
            throw DomainValidationException("이력서 산출물은 양식 스냅샷이 필요해요.")
        }
        if (kind == ArtifactKind.PORTFOLIO && snapshotSectionList.isNotEmpty()) {
            throw DomainValidationException("포트폴리오 산출물은 양식 스냅샷을 가질 수 없어요.")
        }
        versionList.forEach { version ->
            version.assignArtifact(this)
            requireSectionsMatchKind(kind, version)
        }
    }

    /** 버전 목록(읽기 전용 뷰, 생성 순서). */
    val versions: List<Version> get() = versionList.toList()

    /** 현재 활성 버전(불변식상 항상 존재). */
    fun activeVersion(): Version = versionList.first { it.id == activeVersionId }

    /**
     * 항목 채택(구현 설계 §3.5, 수용 기준 19): 직전 활성 버전을 복제한 뒤 대상 항목만 교체한 새 버전을
     * 만들어 활성으로 전환한다. 교체되지 않은 항목은 그대로 복제되어 **한 항목 변경이 다른 항목을 바꾸지 않는다.**
     * 대상 항목은 같은 섹션 정의 키(definitionKey)로 새 버전에서 대응된다.
     *
     * @param sectionId 활성 버전에서 교체할 항목의 식별자.
     * @param adopted   교체할 내용.
     * @return 새로 만들어진 활성 버전.
     */
    fun adoptSection(sectionId: SectionId, adopted: SectionContent, createdAt: Instant): Version {
        val active = activeVersion()
        val target = active.sectionById(sectionId)
            ?: throw DomainValidationException("채택할 항목을 활성 버전에서 찾을 수 없어요.")

        val newSections = active.sections.map { source ->
            val copy = ArtifactSection.copyForNewVersion(source)
            if (source.definitionKey == target.definitionKey) {
                copy.content = adopted
                copy.status = SectionStatus.GENERATED
            }
            copy
        }

        val newVersion = Version.create(newSections, createdAt)
        newVersion.assignArtifact(this)
        versionList.add(newVersion)
        activeVersionId = newVersion.id
        return newVersion
    }

    /**
     * 항목 **일괄 채택**(품질 개선 기획 §3.5 "일괄 채택은 한 번의 버전 전이로 묶어 버전 폭증을 막는다"): 직전 활성
     * 버전을 복제한 뒤 [adopted]에 담긴 여러 항목을 **한 번에** 교체한 새 버전을 만들어 활성으로 전환한다. 교체되지
     * 않은 항목은 그대로 복제되어 **채택하지 않은 항목은 변하지 않는다**(§334). [adoptSection]과 달리 여러 항목을
     * 한 버전 전이로 묶는다(품질 개선 일괄 채택 — 버전 1개·prune 1회).
     *
     * @param adopted   교체할 항목 식별자 → 내용 맵. 비어 있으면 채택할 게 없으므로 거부한다.
     * @return 새로 만들어진 활성 버전.
     */
    fun adoptSections(adopted: Map<SectionId, SectionContent>, createdAt: Instant): Version {
        if (adopted.isEmpty()) {
            throw DomainValidationException("채택할 항목이 없어요.")
        }
        val active = activeVersion()
        // 채택 대상 항목을 definitionKey로 변환한다(새 버전에서 같은 키로 대응 — adoptSection과 동일 규칙).
        val adoptedByKey = adopted.entries.associate { (sectionId, content) ->
            val target = active.sectionById(sectionId)
                ?: throw DomainValidationException("채택할 항목을 활성 버전에서 찾을 수 없어요.")
            target.definitionKey to content
        }

        val newSections = active.sections.map { source ->
            val copy = ArtifactSection.copyForNewVersion(source)
            adoptedByKey[source.definitionKey]?.let { content ->
                copy.content = content
                copy.status = SectionStatus.GENERATED
            }
            copy
        }

        val newVersion = Version.create(newSections, createdAt)
        newVersion.assignArtifact(this)
        versionList.add(newVersion)
        activeVersionId = newVersion.id
        return newVersion
    }

    /**
     * 항목 직접 편집(도메인 이해 §5·§267·§382·§428, 수용 기준 10·19): 직전 활성 버전을 복제한 뒤 대상 항목만
     * content를 교체한 새 버전을 만들어 활성으로 전환한다. 교체되지 않은 항목은 [ArtifactSection.copyForNewVersion]으로
     * 그대로 복제되어 **한 항목 편집이 다른 항목을 바꾸지 않는다.**
     *
     * [adoptSection]과의 차이: 대상 항목을 [ArtifactSection.copyForEditedVersion]으로 복제해
     * **factGroundingList를 빈 목록으로** 만든다. 사용자가 직접 쓴 내용엔 AI 파생 토큰별 근거가 더 이상 대응하지
     * 않으므로(§382) 비워 "factGroundings = AI 파생 근거" 불변식을 정직하게 유지한다.
     * sourceExperienceIds는 "근거 없이 만들어진 항목 0건" 불변식 유지를 위해 보존한다(§428).
     * 직접 편집 결과는 사용자가 확정한 내용이므로 GENERATED로 둔다.
     *
     * @param sectionId 활성 버전에서 편집할 항목의 식별자.
     * @param edited    편집된 내용.
     * @return 새로 만들어진 활성 버전.
     */
    fun editSection(sectionId: SectionId, edited: SectionContent, createdAt: Instant): Version {
        val active = activeVersion()
        val target = active.sectionById(sectionId)
            ?: throw DomainValidationException("편집할 항목을 활성 버전에서 찾을 수 없어요.")

        val newSections = active.sections.map { source ->
            if (source.definitionKey == target.definitionKey) {
                // 대상 항목: factGroundings 비움 + content 교체 + GENERATED.
                val copy = ArtifactSection.copyForEditedVersion(source)
                copy.content = edited
                copy.status = SectionStatus.GENERATED
                copy
            } else {
                // 미변경 항목: 그대로 복제(factGroundings 포함).
                ArtifactSection.copyForNewVersion(source)
            }
        }

        val newVersion = Version.create(newSections, createdAt)
        newVersion.assignArtifact(this)
        versionList.add(newVersion)
        activeVersionId = newVersion.id
        return newVersion
    }

    /**
     * 버전 복원(도메인 이해 §277·§283, 구현 설계 §3.6 "복원 = 활성 전환"): 사용자가 고른 이전 버전을
     * **활성으로 되돌린다**. 채택·편집과 달리 새 버전을 만들지 않고 기존 버전을 그대로 활성으로 재지정한다.
     *
     * **복원 의미론(설계 결정 — 활성 전환):** §287은 복원을 "활성 전환"으로 못박는다. 채택·편집(§344)은 직전
     * 활성 버전을 복제해 **내용을 바꾸므로** 새 버전을 만들지만, 복원은 과거 시점 스냅샷을 **있는 그대로** 다시
     * 보는 행위라 새 버전이 불필요하다(복제하면 같은 내용의 버전이 늘어 보관 상한만 빨리 소진된다). 버전 생성
     * 순서(createdAt)도 보존되어 비교의 시간축이 흐트러지지 않는다. 불변식(활성은 항상 versions 안에 존재)은
     * 대상이 versions 안에 있을 때만 전환해 유지한다.
     *
     * @param versionId 활성으로 되돌릴 버전. 이 산출물의 버전이 아니면 [DomainValidationException].
     * @return 활성으로 전환된 버전.
     */
    fun restoreVersion(versionId: VersionId): Version {
        val target = versionList.firstOrNull { it.id == versionId }
            ?: throw DomainValidationException("되돌릴 버전을 이 산출물에서 찾을 수 없어요.")
        activeVersionId = target.id
        return target
    }

    /**
     * 버전 보관 상한 정리(구현 설계 §3.5, 수용 기준 11): 버전 수가 상한을 초과하면 가장 오래된 버전부터
     * **상한 이하가 되거나 정리 가능한 비활성 버전이 없을 때까지 반복 정리**한다. 단, **활성 버전은 정리
     * 대상에서 제외**한다(항상 활성 버전 존재 불변식 — §135). 사전 고지는 상위 계층 책임.
     *
     * @return 정리된 버전 목록(오래된 순, 정리 대상이 없으면 빈 목록).
     */
    fun pruneOldestIfExceeds(limit: Int): List<Version> {
        if (limit < 1) {
            throw DomainValidationException("버전 보관 상한은 1 이상이어야 해요.")
        }
        val pruned = mutableListOf<Version>()
        while (versionList.size > limit) {
            val oldest = versionList
                .filter { it.id != activeVersionId }
                .minByOrNull { it.createdAt }
                ?: break
            versionList.remove(oldest)
            pruned.add(oldest)
        }
        return pruned
    }

    companion object {
        /**
         * 1차 생성 결과로 초기 버전을 만들어 산출물을 생성한다(구현 설계 §3.5). 부분 실패 항목(*_FAILED)도
         * 포함해 저장 가능하다(수용 기준 9). 초기 버전이 곧 활성 버전이 된다.
         *
         * @param targetSnapshot  생성 시점의 목표 불변 스냅샷(이력서·포트폴리오 모두 필수 — §347).
         * @param templateSnapshot 이력서면 양식 스냅샷, 포트폴리오면 null.
         */
        fun create(
            ownerId: UserId,
            kind: ArtifactKind,
            targetSnapshot: ArtifactTargetSnapshot,
            templateSnapshot: TemplateSnapshot?,
            initialSections: List<ArtifactSection>,
            createdAt: Instant,
        ): Artifact {
            requireSnapshotMatchesKind(kind, templateSnapshot)
            val initialVersion = Version.create(initialSections, createdAt)
            return Artifact(
                id = ArtifactId(IdentifierGenerator.newId()),
                ownerId = ownerId,
                kind = kind,
                targetSnapshot = targetSnapshot,
                snapshotSectionList = (templateSnapshot?.sections ?: emptyList()).toMutableList(),
                versionList = mutableListOf(initialVersion),
                activeVersionId = initialVersion.id,
            )
        }

        fun retrieve(
            id: ArtifactId,
            ownerId: UserId,
            kind: ArtifactKind,
            targetSnapshot: ArtifactTargetSnapshot,
            templateSnapshot: TemplateSnapshot?,
            versions: List<Version>,
            activeVersionId: VersionId,
        ): Artifact = Artifact(
            id = id,
            ownerId = ownerId,
            kind = kind,
            targetSnapshot = targetSnapshot,
            snapshotSectionList = (templateSnapshot?.sections ?: emptyList()).toMutableList(),
            versionList = versions.toMutableList(),
            activeVersionId = activeVersionId,
        )

        private fun requireSnapshotMatchesKind(kind: ArtifactKind, snapshot: TemplateSnapshot?) {
            if (kind == ArtifactKind.RESUME && snapshot == null) {
                throw DomainValidationException("이력서 산출물은 양식 스냅샷이 필요해요.")
            }
            if (kind == ArtifactKind.PORTFOLIO && snapshot != null) {
                throw DomainValidationException("포트폴리오 산출물은 양식 스냅샷을 가질 수 없어요.")
            }
        }

        /**
         * 섹션 종류↔산출물 종류 정합 가드(구현 설계 §166~169).
         * RESUME 산출물의 섹션은 {SUMMARY, CAREER}, PORTFOLIO는 {EXPERIENCE_NARRATIVE}만 허용한다.
         */
        private fun requireSectionsMatchKind(kind: ArtifactKind, version: Version) {
            val allowed = when (kind) {
                ArtifactKind.RESUME -> setOf(SectionKind.SUMMARY, SectionKind.CAREER)
                ArtifactKind.PORTFOLIO -> setOf(SectionKind.EXPERIENCE_NARRATIVE)
            }
            if (version.sections.any { it.sectionKind !in allowed }) {
                throw DomainValidationException("산출물 종류에 맞지 않는 섹션 종류가 포함되어 있어요.")
            }
        }
    }
}
