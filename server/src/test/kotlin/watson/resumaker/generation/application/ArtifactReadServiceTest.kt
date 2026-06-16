package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecordId
import java.time.Instant
import java.util.UUID

/**
 * [ArtifactReadService] 단위 테스트(열람·소유 격리, 수용 기준 12·13).
 * 검증: 활성 버전 항목을 표시용 DTO로 변환, 타인 소유·미존재는 동일하게 404(ResourceNotFoundException).
 */
class ArtifactReadServiceTest {

    private val artifactRepository: ArtifactRepository = mock()
    private val mapper = ArtifactReadServiceMapper()
    private val service = ArtifactReadService(artifactRepository, mapper)

    private val ownerId = UserId(UUID.randomUUID())
    private val artifactId = ArtifactId(UUID.randomUUID())
    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    private fun resumeArtifact(): Artifact {
        val snapshot = TemplateSnapshot.of(
            listOf(SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
        )
        val section = ArtifactSection.create(
            definitionKey = "section-0-요약",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("요약 본문"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        return Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = ArtifactTargetSnapshot.of("백엔드 신입", null, null),
            templateSnapshot = snapshot,
            initialSections = listOf(section),
            createdAt = Instant.parse("2026-06-16T00:00:00Z"),
        )
    }

    @Test
    fun 산출물을_열람하면_활성_버전_항목을_표시용으로_반환한다() {
        // given (수용 기준 12)
        val artifact = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)

        // when
        val response = service.getArtifact(ownerId, artifact.id)

        // then
        assertThat(response.id).isEqualTo(artifact.id.value.toString())
        assertThat(response.kind).isEqualTo(ArtifactKind.RESUME)
        assertThat(response.activeVersion.versionId).isNotBlank()
        assertThat(response.activeVersion.sections).hasSize(1)
        val section = response.activeVersion.sections.first()
        assertThat(section.definitionKey).isEqualTo("section-0-요약")
        assertThat(section.content).isEqualTo("요약 본문")
        assertThat(section.status).isEqualTo(SectionStatus.GENERATED)
        assertThat(section.sourceExperienceIds).containsExactly(exp1.value.toString())
    }

    @Test
    fun 타인_소유이거나_미존재면_찾을_수_없음_예외() {
        // given (소유 격리, 수용 기준 13) — findByIdAndOwnerId가 null을 돌려준다(존재 노출 최소화 → 404).
        whenever(artifactRepository.findByIdAndOwnerId(artifactId, ownerId)).thenReturn(null)

        // when and then
        assertThatThrownBy { service.getArtifact(ownerId, artifactId) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
