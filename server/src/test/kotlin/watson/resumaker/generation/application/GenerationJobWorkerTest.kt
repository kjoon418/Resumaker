package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobStatus
import watson.resumaker.generation.infrastructure.ClaudeCliException
import watson.resumaker.generation.infrastructure.GenerationJobRepository
import watson.resumaker.generation.presentation.GeneratedSectionResponse
import watson.resumaker.generation.presentation.GenerationResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * [GenerationJobWorker] 단위 테스트. 생성 파이프라인은 mock — 워커의 큐 처리·예외 매핑·고아 회수만 본다.
 *
 * 검증: PENDING 클레임→성공 시 SUCCEEDED+artifactId, 예외별 FAILED+코드 매핑, 고아 RUNNING recoverStale로 FAILED,
 * claim 원자성(이미 RUNNING이면 claim 0이라 처리 안 함).
 */
class GenerationJobWorkerTest {

    private val jobRepository: GenerationJobRepository = mock()
    private val generationService: ArtifactGenerationService = mock()
    private val properties = GenerationJobProperties(pollIntervalMs = 2000, staleRunningTimeout = Duration.ofMinutes(5))
    private val now = Instant.parse("2026-06-22T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val worker = GenerationJobWorker(jobRepository, generationService, properties, clock)

    private val ownerId = UserId(UUID.randomUUID())
    private val expId = ExperienceRecordId(UUID.randomUUID())
    private val targetId = UUID.randomUUID()

    private fun pendingResume(): GenerationJob = GenerationJob.create(
        ownerId = ownerId,
        kind = ArtifactKind.RESUME,
        experienceIds = listOf(expId.value),
        targetId = targetId,
        templateId = null,
        targetCompany = "토스",
        createdAt = now.minusSeconds(10),
    )

    private fun generationResponse(artifactId: String) = GenerationResponse(
        artifactId = artifactId,
        kind = ArtifactKind.RESUME,
        activeVersionId = UUID.randomUUID().toString(),
        sections = listOf(
            GeneratedSectionResponse(
                sectionId = UUID.randomUUID().toString(),
                definitionKey = "section-0-요약",
                sectionKind = SectionKind.SUMMARY,
                content = "요약",
                status = SectionStatus.GENERATED,
                sourceExperienceIds = listOf(expId.value.toString()),
                factGroundings = emptyList(),
            ),
        ),
    )

    @Test
    fun 클레임_성공하면_생성_결과로_SUCCEEDED_artifactId를_저장한다() {
        // given
        val job = pendingResume()
        val artifactId = UUID.randomUUID().toString()
        whenever(jobRepository.findFirstByStatusOrderByCreatedAtAsc(GenerationJobStatus.PENDING)).thenReturn(job)
        whenever(jobRepository.claim(any(), any())).thenReturn(1)
        whenever(jobRepository.findById(job.id.value)).thenReturn(Optional.of(job))
        whenever(generationService.generateResume(any(), any())).thenReturn(generationResponse(artifactId))

        // when
        worker.claimAndProcessOne()

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.SUCCEEDED)
        assertThat(job.artifactId).isEqualTo(UUID.fromString(artifactId))
        assertThat(job.finishedAt).isEqualTo(now)
        verify(jobRepository).save(job)
    }

    @Test
    fun 이미_RUNNING이면_claim이_0이라_처리하지_않는다() {
        // given (claim 원자성) — 다른 호출이 먼저 가져갔다.
        val job = pendingResume()
        whenever(jobRepository.findFirstByStatusOrderByCreatedAtAsc(GenerationJobStatus.PENDING)).thenReturn(job)
        whenever(jobRepository.claim(any(), any())).thenReturn(0)

        // when
        worker.claimAndProcessOne()

        // then — reload·생성·저장 모두 일어나지 않는다.
        verify(jobRepository, never()).findById(any())
        verify(generationService, never()).generateResume(any(), any())
        verify(jobRepository, never()).save(any())
    }

    @Test
    fun 한도초과_예외는_코드를_보존해_FAILED로_저장한다() {
        // given
        val job = pendingResume()
        doThrow(QuotaExceededException("한도 초과", code = "GENERATION_QUOTA_EXCEEDED"))
            .whenever(generationService).generateResume(any(), any())

        // when
        worker.process(job)

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("GENERATION_QUOTA_EXCEEDED")
        verify(jobRepository).save(job)
    }

    @Test
    fun CLI_예외는_AI_불가_코드로_FAILED() {
        // given
        val job = pendingResume()
        doThrow(ClaudeCliException("cli down")).whenever(generationService).generateResume(any(), any())

        // when
        worker.process(job)

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("AI_GENERATION_UNAVAILABLE")
    }

    @Test
    fun 전항목_실패_도메인검증_예외는_NO_CONTENT로_FAILED() {
        // given
        val job = pendingResume()
        doThrow(DomainValidationException("생성할 수 있는 항목이 없어요."))
            .whenever(generationService).generateResume(any(), any())

        // when
        worker.process(job)

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("GENERATION_NO_CONTENT")
    }

    @Test
    fun 원본_삭제_미존재_예외는_SOURCE_MISSING으로_FAILED() {
        // given
        val job = pendingResume()
        doThrow(ResourceNotFoundException("경험 없음"))
            .whenever(generationService).generateResume(any(), any())

        // when
        worker.process(job)

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("GENERATION_SOURCE_MISSING")
    }

    @Test
    fun 그_외_예외는_GENERATION_FAILED로_FAILED() {
        // given
        val job = pendingResume()
        doThrow(RuntimeException("boom")).whenever(generationService).generateResume(any(), any())

        // when
        worker.process(job)

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("GENERATION_FAILED")
    }

    @Test
    fun 고아_RUNNING은_recoverStale로_FAILED_종료된다() {
        // given — 너무 오래 RUNNING인 작업(startedAt이 staleTimeout 이전).
        val job = pendingResume()
        // RUNNING으로 만들고 startedAt을 과거로 두기 위해 claim 시뮬레이션 대신 직접 회수 대상으로 구성.
        whenever(
            jobRepository.findByStatusAndStartedAtBefore(eq(GenerationJobStatus.RUNNING), any()),
        ).thenReturn(listOf(job))

        // when
        worker.recoverStale()

        // then
        assertThat(job.status).isEqualTo(GenerationJobStatus.FAILED)
        assertThat(job.errorCode).isEqualTo("AI_GENERATION_UNAVAILABLE")
        assertThat(job.finishedAt).isEqualTo(now)
        verify(jobRepository).save(job)
    }
}
