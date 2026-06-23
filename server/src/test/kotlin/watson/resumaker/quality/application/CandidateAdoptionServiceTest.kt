package watson.resumaker.quality.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.ArtifactReadServiceMapper
import watson.resumaker.generation.infrastructure.ArtifactVersioningProperties
import watson.resumaker.quality.domain.QualityCandidate
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobId
import watson.resumaker.quality.infrastructure.QualityCandidateRepository
import watson.resumaker.quality.infrastructure.QualityImprovementJobRepository
import watson.resumaker.target.domain.RecruitDirection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * [CandidateAdoptionService] 단위 테스트(채택, QC5). 레포는 mock, 도메인 Artifact·읽기 매퍼는 실제.
 *
 * 검증: 채택은 새 활성 버전을 만들고(미채택 항목 불변), 일괄 채택은 한 버전 전이, 차감 없음(작업 성공 시 이미 차감),
 * 작업·산출물 소유 격리(404), 후보 미존재(404).
 */
class CandidateAdoptionServiceTest {

    private val jobRepository: QualityImprovementJobRepository = mock()
    private val candidateRepository: QualityCandidateRepository = mock()
    private val artifactRepository: ArtifactRepository = mock()
    private val mapper = ArtifactReadServiceMapper()
    private val versioningProperties = ArtifactVersioningProperties(versionRetentionLimit = 10)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)
    private val service = CandidateAdoptionService(
        jobRepository, candidateRepository, artifactRepository, mapper, versioningProperties, clock,
    )

    private val ownerId = UserId(UUID.randomUUID())

    private fun resumeArtifact(): Pair<Artifact, Map<String, SectionId>> {
        val summary = ArtifactSection.create(
            "section-0-요약", SectionKind.SUMMARY, SectionContent.of("원래 요약"), SectionStatus.GENERATED, emptyList(), emptyList(),
        )
        val career = ArtifactSection.create(
            "section-1-경력", SectionKind.CAREER, SectionContent.of("원래 경력"), SectionStatus.GENERATED, emptyList(), emptyList(),
        )
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = ArtifactTargetSnapshot.of(RecruitDirection("백엔드 신입"), null, null),
            templateSnapshot = TemplateSnapshot.of(
                listOf(
                    SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
                    SnapshotSection.of("section-1-경력", "경력", SectionKind.CAREER, required = true),
                ),
            ),
            initialSections = listOf(summary, career),
            createdAt = Instant.parse("2026-06-16T00:00:00Z"),
        )
        return artifact to mapOf("summary" to summary.id, "career" to career.id)
    }

    private fun job(artifact: Artifact): QualityImprovementJob = QualityImprovementJob.create(
        ownerId = ownerId,
        artifactId = artifact.id.value,
        versionId = artifact.activeVersion().id.value,
        findingIds = listOf("x:I1"),
        createdAt = Instant.parse("2026-06-22T00:00:00Z"),
    )

    private fun candidate(jobId: UUID, sectionId: SectionId, key: String, content: String): QualityCandidate =
        QualityCandidate.create(jobId, sectionId, key, "원본", content, listOf("I1"))

    @Test
    fun 일괄_채택은_한_버전_전이로_새_활성_버전을_만든다() {
        // given (QC5) — 두 후보를 일괄 채택.
        val (artifact, ids) = resumeArtifact()
        val j = job(artifact)
        val c1 = candidate(j.id.value, ids["summary"]!!, "section-0-요약", "다듬은 요약")
        val c2 = candidate(j.id.value, ids["career"]!!, "section-1-경력", "다듬은 경력")
        whenever(jobRepository.findByIdAndOwnerId(any(), any())).thenReturn(j)
        whenever(candidateRepository.findAllByJobId(j.id.value)).thenReturn(listOf(c1, c2))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }

        // when
        val response = service.adopt(ownerId, artifact.id.value, j.id, listOf(c1.id.value.toString(), c2.id.value.toString()))

        // then — 버전 1개만 늘고(폭증 없음), 두 항목 모두 교체.
        assertThat(artifact.versions).hasSize(2)
        val sections = response.activeVersion.sections.associateBy { it.definitionKey }
        assertThat(sections["section-0-요약"]!!.content).isEqualTo("다듬은 요약")
        assertThat(sections["section-1-경력"]!!.content).isEqualTo("다듬은 경력")
    }

    @Test
    fun 부분_채택은_채택하지_않은_항목을_바꾸지_않는다() {
        // given (QC5 미채택 불변) — summary만 채택.
        val (artifact, ids) = resumeArtifact()
        val j = job(artifact)
        val c1 = candidate(j.id.value, ids["summary"]!!, "section-0-요약", "다듬은 요약")
        whenever(jobRepository.findByIdAndOwnerId(any(), any())).thenReturn(j)
        whenever(candidateRepository.findAllByJobId(j.id.value)).thenReturn(listOf(c1))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }

        // when
        val response = service.adopt(ownerId, artifact.id.value, j.id, listOf(c1.id.value.toString()))

        // then — career는 원본 유지.
        val sections = response.activeVersion.sections.associateBy { it.definitionKey }
        assertThat(sections["section-0-요약"]!!.content).isEqualTo("다듬은 요약")
        assertThat(sections["section-1-경력"]!!.content).isEqualTo("원래 경력")
    }

    @Test
    fun 타인_소유이거나_미존재_작업이면_404() {
        // given (QC8) — 작업 조회가 null.
        whenever(jobRepository.findByIdAndOwnerId(any(), any())).thenReturn(null)

        // when and then
        assertThatThrownBy {
            service.adopt(ownerId, UUID.randomUUID(), QualityImprovementJobId(UUID.randomUUID()), listOf(UUID.randomUUID().toString()))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 요청한_후보가_이_작업에_없으면_404() {
        // given — 후보 목록은 있으나 요청 candidateId가 그 안에 없다.
        val (artifact, ids) = resumeArtifact()
        val j = job(artifact)
        val c1 = candidate(j.id.value, ids["summary"]!!, "section-0-요약", "다듬은 요약")
        whenever(jobRepository.findByIdAndOwnerId(any(), any())).thenReturn(j)
        whenever(candidateRepository.findAllByJobId(j.id.value)).thenReturn(listOf(c1))

        // when and then — 무관한 candidateId 요청 → 404.
        assertThatThrownBy {
            service.adopt(ownerId, artifact.id.value, j.id, listOf(UUID.randomUUID().toString()))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }
}
