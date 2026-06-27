package watson.resumaker.quality.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
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
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.quality.domain.QualityCandidate
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobStatus
import watson.resumaker.quality.infrastructure.QualityCandidateRepository
import watson.resumaker.quality.infrastructure.QualityImprovementJobRepository
import watson.resumaker.target.domain.RecruitDirection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * [QualityImprovementJobWorker] 단위 테스트. 처치 프로세서·레포는 mock — 워커의 큐 처리·고아 회수·후보 영속·상태
 * 확정만 본다. TransactionTemplate은 콜백을 즉시 실행하는 fake로 둔다(tx 경계 행위 검증).
 *
 * 검증: 클레임→후보 ≥1 영속→SUCCEEDED, 후보 0건→FAILED(원본 유지), 고아 RUNNING recoverStale→FAILED(QC6),
 * claim 0이면 처리 안 함(원자성).
 */
class QualityImprovementJobWorkerTest {

    private val jobRepository: QualityImprovementJobRepository = mock()
    private val candidateRepository: QualityCandidateRepository = mock()
    private val artifactRepository: ArtifactRepository = mock()
    private val experienceRepository: ExperienceRecordRepository = mock()
    private val processor: QualityImprovementProcessor = mock()
    private val properties = QualityImprovementJobProperties(pollIntervalMs = 2000, staleRunningTimeout = Duration.ofMinutes(5))
    private val now = Instant.parse("2026-06-22T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    /** 콜백을 즉시 실행하는 fake TransactionTemplate(tx 경계 시뮬레이션). */
    private val transactionTemplate: TransactionTemplate = mock {
        on { execute(any<TransactionCallback<Any>>()) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any>
            callback.doInTransaction(mock())
        }
    }

    private val quotaGuard = watson.resumaker.generation.application.AllowingGenerationQuotaGuard()

    private val worker = QualityImprovementJobWorker(
        jobRepository, candidateRepository, artifactRepository, experienceRepository,
        processor, quotaGuard, properties, transactionTemplate, clock,
    )

    private val ownerId = UserId(UUID.randomUUID())

    private fun resumeArtifactWithSection(): Pair<Artifact, SectionId> {
        val section = ArtifactSection.create(
            definitionKey = "section-0-요약",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("결제를 담당했다."),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = emptyList(),
            factGroundings = emptyList(),
        )
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = ArtifactTargetSnapshot.of(RecruitDirection("백엔드 신입"), null, null),
            templateSnapshot = TemplateSnapshot.of(
                listOf(SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
            ),
            initialSections = listOf(section),
            createdAt = now,
        )
        return artifact to section.id
    }

    private fun pendingJob(artifact: Artifact, sectionId: SectionId): QualityImprovementJob = QualityImprovementJob.create(
        ownerId = ownerId,
        artifactId = artifact.id.value,
        versionId = artifact.activeVersion().id.value,
        findingIds = listOf("${sectionId.value}:I1"),
        createdAt = now.minusSeconds(10),
    )

    private fun candidate() = GeneratedSection(
        definitionKey = "section-0-요약",
        sectionKind = SectionKind.SUMMARY,
        content = "결제 시스템을 설계·운영했어요.",
        succeeded = true,
        sourceExperienceIds = emptyList(),
        factGroundings = emptyList(),
    )

    @Test
    fun 후보가_하나라도_나오면_영속하고_SUCCEEDED로_종료한다() {
        // given
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(processor.process(any())).thenReturn(candidate())

        // when
        worker.process(job)

        // then — 후보 영속 + SUCCEEDED.
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.SUCCEEDED)
        verify(candidateRepository).saveAll(any<List<QualityCandidate>>())
        verify(jobRepository).save(job)
    }

    @Test
    fun 모든_항목_검증실패면_후보없이_FAILED로_종료한다() {
        // given — 프로세서가 모두 null(검증 실패·재시도도 실패).
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(processor.process(any())).thenReturn(null)

        // when
        worker.process(job)

        // then — 후보 미영속, FAILED(원본 유지).
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("QUALITY_IMPROVEMENT_NO_CANDIDATE")
        verify(candidateRepository, never()).saveAll(any<List<QualityCandidate>>())
    }

    @Test
    fun 접수후_활성버전이_바뀌어_소견항목이_없으면_NO_CONTENT로_FAILED() {
        // given — 작업의 versionId가 현재 활성 버전과 다르다(버전 불일치).
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = QualityImprovementJob.create(
            ownerId = ownerId,
            artifactId = artifact.id.value,
            versionId = UUID.randomUUID(), // 활성 버전과 불일치.
            findingIds = listOf("${sectionId.value}:I1"),
            createdAt = now.minusSeconds(10),
        )
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)

        // when
        worker.process(job)

        // then
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("QUALITY_IMPROVEMENT_NO_CONTENT")
        verify(processor, never()).process(any())
    }

    @Test
    fun 고아_RUNNING은_recoverStale로_FAILED_종료된다() {
        // given (QC6) — 너무 오래 RUNNING.
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(
            jobRepository.findByStatusAndStartedAtBefore(eq(QualityImprovementJobStatus.RUNNING), any()),
        ).thenReturn(listOf(job))

        // when
        worker.recoverStale()

        // then
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("QUALITY_IMPROVEMENT_UNAVAILABLE")
        assertThat(job.finishedAt).isEqualTo(now)
        verify(jobRepository).save(job)
    }

    @Test
    fun 이미_RUNNING이면_claim이_0이라_처리하지_않는다() {
        // given (claim 원자성).
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(jobRepository.findFirstByStatusOrderByCreatedAtAsc(QualityImprovementJobStatus.PENDING)).thenReturn(job)
        whenever(jobRepository.claim(any(), any())).thenReturn(0)

        // when
        worker.claimAndProcessOne()

        // then — reload·처치 모두 일어나지 않는다.
        verify(jobRepository, never()).findById(any())
        verify(processor, never()).process(any())
    }

    @Test
    fun 처리시점_한도소진이면_차감_없이_QUOTA초과로_종료한다() {
        // given (B2) — 접수는 통과했지만 처리 시점엔 한도가 소진됐다(시차 우회 시뮬레이션).
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(processor.process(any())).thenReturn(candidate())
        val exhaustedGuard = ExhaustibleQuotaGuard(qualityExhausted = true)
        val quotaAwareWorker = QualityImprovementJobWorker(
            jobRepository, candidateRepository, artifactRepository, experienceRepository,
            processor, exhaustedGuard, properties, transactionTemplate, clock,
        )

        // when
        quotaAwareWorker.process(job)

        // then — 후보 미영속·미차감, QUOTA 초과 코드로 FAILED.
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("QUALITY_IMPROVEMENT_QUOTA_EXCEEDED")
        verify(candidateRepository, never()).saveAll(any<List<QualityCandidate>>())
        assertThat(exhaustedGuard.qualityRecorded).isEqualTo(0)
    }

    @Test
    fun 처리시점_한도_여유면_후보영속하고_차감한다() {
        // given (B2 대칭) — 처리 시점 점검 통과면 평소대로 후보 영속·차감.
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(processor.process(any())).thenReturn(candidate())
        val freshGuard = ExhaustibleQuotaGuard(qualityExhausted = false)
        val quotaAwareWorker = QualityImprovementJobWorker(
            jobRepository, candidateRepository, artifactRepository, experienceRepository,
            processor, freshGuard, properties, transactionTemplate, clock,
        )

        // when
        quotaAwareWorker.process(job)

        // then
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.SUCCEEDED)
        verify(candidateRepository).saveAll(any<List<QualityCandidate>>())
        assertThat(freshGuard.qualityRecorded).isEqualTo(1)
    }

    /** 품질 개선 한도를 소진 상태로 시뮬레이트하는 fake 가드. checkQualityImprovement만 의미 있게 구현한다. */
    private class ExhaustibleQuotaGuard(
        private val qualityExhausted: Boolean,
    ) : watson.resumaker.generation.application.GenerationQuotaGuard {
        var qualityRecorded = 0
        override fun checkInitialGeneration(ownerId: UserId) {}
        override fun recordInitialGeneration(ownerId: UserId) {}
        override fun checkRegeneration(ownerId: UserId, artifactId: watson.resumaker.artifact.domain.ArtifactId, definitionKey: String) {}
        override fun recordRegeneration(ownerId: UserId, artifactId: watson.resumaker.artifact.domain.ArtifactId, definitionKey: String) {}
        override fun checkQualityImprovement(ownerId: UserId) {
            if (qualityExhausted) {
                throw watson.resumaker.common.domain.QuotaExceededException(
                    message = "한도 초과",
                    code = "QUALITY_IMPROVEMENT_QUOTA_EXCEEDED",
                    action = "EDIT_MANUALLY",
                )
            }
        }
        override fun recordQualityImprovement(ownerId: UserId) { qualityRecorded++ }
    }

    @Test
    fun 클레임_성공하면_reload후_처치한다() {
        // given
        val (artifact, sectionId) = resumeArtifactWithSection()
        val job = pendingJob(artifact, sectionId)
        whenever(jobRepository.findFirstByStatusOrderByCreatedAtAsc(QualityImprovementJobStatus.PENDING)).thenReturn(job)
        whenever(jobRepository.claim(any(), any())).thenReturn(1)
        whenever(jobRepository.findById(job.id.value)).thenReturn(Optional.of(job))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(processor.process(any())).thenReturn(candidate())

        // when
        worker.claimAndProcessOne()

        // then
        assertThat(job.status).isEqualTo(QualityImprovementJobStatus.SUCCEEDED)
    }
}
