package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.artifact.domain.VersionId
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecordId
import java.time.Instant
import java.util.UUID

/**
 * [VersionRestoreService] 단위 테스트(버전 복원, 도메인 이해 §277·§283, "복원 = 활성 전환", 수용 기준 11·12).
 *
 * 검증: 복원 성공 → 활성 전환·이전 버전 보존·새 버전 미생성, 소유 격리·미존재(산출물/버전) 404.
 * 복원은 AI 비호출(외부 의존 없음)이라 fake 포트·검증기·락이 없는 순수 동기 영속 작업이다.
 */
class VersionRestoreServiceTest {

    private val artifactRepository: ArtifactRepository = mock()
    private val mapper = ArtifactReadServiceMapper()

    private val ownerId = UserId(UUID.randomUUID())
    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    private val service = VersionRestoreService(
        artifactRepository = artifactRepository,
        mapper = mapper,
    )

    private fun targetSnapshot(): ArtifactTargetSnapshot =
        ArtifactTargetSnapshot.of(recruitDirection = RecruitDirection("백엔드 신입"), company = null, job = null)

    /** 요약 항목을 가진 이력서를 만들고, 편집으로 두 번째 버전을 추가해 (산출물, v1Id, v2Id)를 돌려준다. */
    private fun twoVersionResume(): Triple<Artifact, VersionId, VersionId> {
        val snapshot = TemplateSnapshot.of(
            listOf(SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
        )
        val summary = ArtifactSection.create(
            definitionKey = "section-0-요약",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("v1 요약"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = snapshot,
            initialSections = listOf(summary),
            createdAt = Instant.parse("2026-06-15T00:00:00Z"),
        )
        val v1Id = artifact.activeVersion().id
        artifact.editSection(summary.id, SectionContent.of("v2 요약"), Instant.parse("2026-06-16T00:00:00Z"))
        val v2Id = artifact.activeVersion().id
        return Triple(artifact, v1Id, v2Id)
    }

    private fun command(artifactId: ArtifactId, versionId: VersionId) =
        RestoreVersionCommand(artifactId = artifactId, versionId = versionId)

    private fun stubSave() {
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 복원에_성공하면_고른_버전이_활성으로_전환되고_이전_버전이_보존된다() {
        // given (§277·§283) — v2가 활성인 상태에서 v1으로 복원.
        val (artifact, v1Id, v2Id) = twoVersionResume()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubSave()

        // when
        val response = service.restoreVersion(ownerId, command(artifact.id, v1Id))

        // then — 활성이 v1으로 전환, v2 보존, 새 버전 미생성(버전 2개 유지).
        assertThat(response.activeVersion.versionId).isEqualTo(v1Id.value.toString())
        assertThat(response.activeVersion.sections.single().content).isEqualTo("v1 요약")
        assertThat(artifact.activeVersion().id).isEqualTo(v1Id)
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.versions.map { it.id }).contains(v2Id)
        // 복원은 버전을 추가하지 않으므로 정리 고지도 0이다.
        assertThat(response.prunedVersionCount).isEqualTo(0)
    }

    @Test
    fun 타인_소유이거나_미존재_산출물이면_404() {
        // given (소유 격리) — findByIdAndOwnerId가 null.
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(null)

        // when and then
        assertThatThrownBy {
            service.restoreVersion(ownerId, command(ArtifactId(UUID.randomUUID()), VersionId(UUID.randomUUID())))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 이_산출물에_없는_버전으로_복원하면_404() {
        // given — 산출물은 있지만 versionId가 그 산출물에 없다(미존재·타인 버전 동일 404).
        val (artifact, _, _) = twoVersionResume()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)

        // when and then
        assertThatThrownBy {
            service.restoreVersion(ownerId, command(artifact.id, VersionId(UUID.randomUUID())))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }
}
