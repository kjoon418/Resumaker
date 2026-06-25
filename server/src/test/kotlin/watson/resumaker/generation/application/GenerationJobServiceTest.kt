package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobId
import watson.resumaker.generation.domain.GenerationJobStatus
import watson.resumaker.generation.infrastructure.GenerationJobRepository
import watson.resumaker.generation.presentation.GenerationJobMapper
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.target.infrastructure.TargetBriefRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * [GenerationJobService] 단위 테스트(제출·조회·삭제, 소유 격리). 외부 LLM 미호출 — 제출은 PENDING 작업만 만든다.
 *
 * 검증: 제출 시 PENDING 작업 생성·jobId 반환, 한도초과 시 작업 미생성(429), 목표 미존재 404, list/get 소유격리,
 * delete 활성작업 409·종료작업 삭제.
 */
class GenerationJobServiceTest {

    private val jobRepository: GenerationJobRepository = mock()
    private val targetRepository: TargetBriefRepository = mock()
    private val quotaGuard: GenerationQuotaGuard = mock()
    private val mapper = GenerationJobMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC)

    private val service = GenerationJobService(jobRepository, targetRepository, quotaGuard, mapper, clock)

    private val ownerId = UserId(UUID.randomUUID())
    private val targetId = TargetBriefId(UUID.randomUUID())
    private val expId = ExperienceRecordId(UUID.randomUUID())

    private fun target(): TargetBrief = TargetBrief.retrieve(
        id = targetId,
        ownerId = ownerId,
        recruitDirection = RecruitDirection("백엔드 신입"),
        company = CompanyName("토스"),
        job = null,
    )

    private fun resumeCommand() = GenerateResumeCommand(listOf(expId), targetId, templateId = null)

    @Test
    fun 이력서_제출하면_PENDING_작업을_만들고_jobId를_반환한다() {
        // given
        whenever(targetRepository.findByIdAndOwnerId(targetId, ownerId)).thenReturn(target())
        whenever(jobRepository.save(any<GenerationJob>())).thenAnswer { it.arguments[0] }

        // when
        val response = service.submitResume(ownerId, resumeCommand())

        // then — PENDING·RESUME·회사명 비정규화·jobId 반환.
        assertThat(response.jobId).isNotBlank()
        assertThat(response.kind).isEqualTo(ArtifactKind.RESUME)
        assertThat(response.status).isEqualTo(GenerationJobStatus.PENDING)
        assertThat(response.targetCompany).isEqualTo("토스")
        assertThat(response.artifactId).isNull()
        verify(jobRepository).save(any<GenerationJob>())
    }

    @Test
    fun 포트폴리오_제출하면_PENDING_PORTFOLIO_작업을_만든다() {
        // given
        whenever(targetRepository.findByIdAndOwnerId(targetId, ownerId)).thenReturn(target())
        whenever(jobRepository.save(any<GenerationJob>())).thenAnswer { it.arguments[0] }

        // when
        val response = service.submitPortfolio(ownerId, GeneratePortfolioCommand(listOf(expId), targetId))

        // then
        assertThat(response.kind).isEqualTo(ArtifactKind.PORTFOLIO)
        assertThat(response.status).isEqualTo(GenerationJobStatus.PENDING)
    }

    @Test
    fun 한도_초과면_작업을_만들지_않고_예외를_던진다() {
        // given (수용 기준 15) — 사전 점검에서 막히면 작업 미생성·목표 적재도 하지 않는다.
        doThrow(QuotaExceededException("한도 초과", code = "GENERATION_QUOTA_EXCEEDED"))
            .whenever(quotaGuard).checkInitialGeneration(ownerId)

        // when and then
        assertThatThrownBy { service.submitResume(ownerId, resumeCommand()) }
            .isInstanceOf(QuotaExceededException::class.java)
        verify(jobRepository, never()).save(any<GenerationJob>())
    }

    @Test
    fun 목표가_없으면_404() {
        // given (소유 격리) — 목표 미존재·타인 소유 모두 404.
        whenever(targetRepository.findByIdAndOwnerId(targetId, ownerId)).thenReturn(null)

        // when and then
        assertThatThrownBy { service.submitResume(ownerId, resumeCommand()) }
            .isInstanceOf(ResourceNotFoundException::class.java)
        verify(jobRepository, never()).save(any<GenerationJob>())
    }

    @Test
    fun 목록은_소유자의_작업을_최신순으로_매핑한다() {
        // given
        val job = pendingJob()
        whenever(jobRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(listOf(job))

        // when
        val responses = service.list(ownerId)

        // then
        assertThat(responses).hasSize(1)
        assertThat(responses[0].jobId).isEqualTo(job.id.value.toString())
    }

    @Test
    fun 단건_조회는_타인_소유이거나_미존재면_404() {
        // given (소유 격리)
        val id = GenerationJobId(UUID.randomUUID())
        whenever(jobRepository.findByIdAndOwnerId(id, ownerId)).thenReturn(null)

        // when and then
        assertThatThrownBy { service.get(ownerId, id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 활성_작업은_삭제할_수_없다_409() {
        // given — PENDING/RUNNING은 처리 중이라 삭제 불가.
        val job = pendingJob()
        whenever(jobRepository.findByIdAndOwnerId(job.id, ownerId)).thenReturn(job)

        // when and then
        assertThatThrownBy { service.delete(ownerId, job.id) }
            .isInstanceOf(ConflictException::class.java)
        verify(jobRepository, never()).delete(any<GenerationJob>())
    }

    @Test
    fun 종료된_작업은_삭제된다() {
        // given — SUCCEEDED는 종료 작업이라 삭제 가능.
        val job = pendingJob().apply { markSucceeded(UUID.randomUUID(), Instant.now(clock)) }
        whenever(jobRepository.findByIdAndOwnerId(job.id, ownerId)).thenReturn(job)

        // when
        service.delete(ownerId, job.id)

        // then
        verify(jobRepository).delete(job)
    }

    @Test
    fun 삭제_대상이_타인_소유이거나_미존재면_404() {
        // given (소유 격리)
        val id = GenerationJobId(UUID.randomUUID())
        whenever(jobRepository.findByIdAndOwnerId(id, ownerId)).thenReturn(null)

        // when and then
        assertThatThrownBy { service.delete(ownerId, id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 일시적_실패_작업은_저장된_입력으로_새_PENDING을_만들고_실패작업을_삭제한다() {
        // given — IN_PLACE(일시적) 실패 작업. 저장된 경험·목표·양식 그대로 다시 만든다.
        val failed = pendingJob().apply {
            markFailed("AI_GENERATION_UNAVAILABLE", "지금은 AI 생성을 사용할 수 없어요.", Instant.now(clock))
        }
        whenever(jobRepository.findByIdAndOwnerId(failed.id, ownerId)).thenReturn(failed)
        whenever(targetRepository.findByIdAndOwnerId(targetId, ownerId)).thenReturn(target())
        whenever(jobRepository.save(any<GenerationJob>())).thenAnswer { it.arguments[0] }

        // when
        val response = service.retryInPlace(ownerId, failed.id)

        // then — 새 PENDING(다른 jobId)으로 교체되고, 입력은 보존되며, 실패 작업은 삭제된다.
        assertThat(response.status).isEqualTo(GenerationJobStatus.PENDING)
        assertThat(response.jobId).isNotEqualTo(failed.id.value.toString())
        assertThat(response.kind).isEqualTo(ArtifactKind.RESUME)
        assertThat(response.experienceIds).containsExactly(expId.value.toString())
        verify(jobRepository).save(any<GenerationJob>())
        verify(jobRepository).delete(failed)
    }

    @Test
    fun 입력오류_실패_작업의_다시만들기는_409() {
        // given — EDIT_INPUTS(입력 오류)는 같은 입력으론 또 실패하므로 in-place 재요청을 막는다(클라이언트는 제작 화면으로).
        val failed = pendingJob().apply {
            markFailed("GENERATION_NO_CONTENT", "생성할 수 있는 항목이 없어요.", Instant.now(clock))
        }
        whenever(jobRepository.findByIdAndOwnerId(failed.id, ownerId)).thenReturn(failed)

        // when and then — 가드레일 점검·저장 없이 409.
        assertThatThrownBy { service.retryInPlace(ownerId, failed.id) }
            .isInstanceOf(ConflictException::class.java)
        verify(quotaGuard, never()).checkInitialGeneration(ownerId)
        verify(jobRepository, never()).save(any<GenerationJob>())
        verify(jobRepository, never()).delete(any<GenerationJob>())
    }

    @Test
    fun 다시만들기도_한도_초과면_새_작업을_만들지_않는다() {
        // given — IN_PLACE라도 제출과 동일하게 가드레일을 사전 점검한다(429). 실패 작업은 그대로 남는다.
        val failed = pendingJob().apply {
            markFailed("AI_GENERATION_UNAVAILABLE", "지금은 AI 생성을 사용할 수 없어요.", Instant.now(clock))
        }
        whenever(jobRepository.findByIdAndOwnerId(failed.id, ownerId)).thenReturn(failed)
        doThrow(QuotaExceededException("한도 초과", code = "GENERATION_QUOTA_EXCEEDED"))
            .whenever(quotaGuard).checkInitialGeneration(ownerId)

        // when and then
        assertThatThrownBy { service.retryInPlace(ownerId, failed.id) }
            .isInstanceOf(QuotaExceededException::class.java)
        verify(jobRepository, never()).save(any<GenerationJob>())
        verify(jobRepository, never()).delete(any<GenerationJob>())
    }

    @Test
    fun 다시만들기_대상이_타인_소유이거나_미존재면_404() {
        // given (소유 격리)
        val id = GenerationJobId(UUID.randomUUID())
        whenever(jobRepository.findByIdAndOwnerId(id, ownerId)).thenReturn(null)

        // when and then
        assertThatThrownBy { service.retryInPlace(ownerId, id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    private fun pendingJob(): GenerationJob = GenerationJob.create(
        ownerId = ownerId,
        kind = ArtifactKind.RESUME,
        experienceIds = listOf(expId.value),
        targetId = targetId.value,
        templateId = null,
        targetCompany = "토스",
        createdAt = Instant.now(clock),
    )
}
