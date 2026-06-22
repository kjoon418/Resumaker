package watson.resumaker.target.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.target.domain.CompanyName
import watson.resumaker.target.domain.JobTitle
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.StrategyStatus
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.WritingStrategy
import watson.resumaker.target.infrastructure.TargetBriefRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * [TargetStrategyWorker] 단위 테스트. 추출기·레포는 mock — 워커의 claim·조건부 쓰기·고아 회수만 본다.
 *
 * 검증: PENDING claim→Extracted면 결과 쓰기, Unavailable면 실패 쓰기, claim 0이면 처리 안 함,
 * 고아 EXTRACTING은 recoverStale로 조건부 실패 쓰기, 조건부 쓰기 0행 폐기는 레포 계약(여기선 호출만 단정).
 */
class TargetStrategyWorkerTest {

    private val repository: TargetBriefRepository = mock()
    private val extractor: TargetStrategyExtractor = mock()
    private val properties = TargetStrategyProperties(pollIntervalMs = 2000, staleRunningTimeout = Duration.ofMinutes(5))
    private val now = Instant.parse("2026-06-22T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val worker = TargetStrategyWorker(repository, extractor, properties, objectMapper, clock)

    private val ownerId = UserId(UUID.randomUUID())

    private fun pendingTarget(): TargetBrief = TargetBrief.create(
        ownerId = ownerId,
        recruitDirection = RecruitDirection("대용량 트래픽 백엔드 공고 전문"),
        company = CompanyName("토스"),
        job = JobTitle("백엔드 개발자"),
    )

    private val strategy = WritingStrategy(
        keywords = listOf("대용량 트래픽"),
        tone = "성과 중심",
        emphasize = listOf("백엔드"),
        avoid = emptyList(),
        summary = "백엔드 신입 강조",
    )

    @Test
    fun PENDING_claim_성공하면_추출해_결과를_조건부로_쓴다() {
        // given
        val target = pendingTarget()
        whenever(repository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING)).thenReturn(target)
        whenever(repository.claimStrategyExtraction(any(), any())).thenReturn(1)
        whenever(repository.findById(any())).thenReturn(Optional.of(target))
        whenever(extractor.extract(any(), any(), any())).thenReturn(StrategyExtraction.Extracted(strategy))

        // when
        worker.claimAndExtractOne()

        // then — 추출은 claim 시점 채용 방향으로, 결과는 직렬화 JSON으로 조건부 쓰기.
        verify(extractor).extract("대용량 트래픽 백엔드 공고 전문", "토스", "백엔드 개발자")
        val expectedJson = objectMapper.writeValueAsString(strategy)
        verify(repository).writeStrategyResult(target.id, expectedJson)
        verify(repository, never()).markStrategyFailed(any())
    }

    @Test
    fun 추출_불가면_실패를_조건부로_쓴다() {
        // given
        val target = pendingTarget()
        whenever(repository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING)).thenReturn(target)
        whenever(repository.claimStrategyExtraction(any(), any())).thenReturn(1)
        whenever(repository.findById(any())).thenReturn(Optional.of(target))
        whenever(extractor.extract(any(), any(), any())).thenReturn(StrategyExtraction.Unavailable)

        // when
        worker.claimAndExtractOne()

        // then
        verify(repository).markStrategyFailed(target.id)
        verify(repository, never()).writeStrategyResult(any(), any())
    }

    @Test
    fun claim이_0이면_추출하지_않는다() {
        // given (원자성) — 다른 호출이 먼저 클레임했다.
        val target = pendingTarget()
        whenever(repository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING)).thenReturn(target)
        whenever(repository.claimStrategyExtraction(any(), any())).thenReturn(0)

        // when
        worker.claimAndExtractOne()

        // then — reload·추출·쓰기 모두 일어나지 않는다.
        verify(repository, never()).findById(any())
        verify(extractor, never()).extract(any(), any(), any())
        verify(repository, never()).writeStrategyResult(any(), any())
        verify(repository, never()).markStrategyFailed(any())
    }

    @Test
    fun PENDING이_없으면_아무것도_하지_않는다() {
        // given
        whenever(repository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING)).thenReturn(null)

        // when
        worker.claimAndExtractOne()

        // then
        verify(repository, never()).claimStrategyExtraction(any(), any())
        verify(extractor, never()).extract(any(), any(), any())
    }

    @Test
    fun 고아_EXTRACTING은_recoverStale로_조건부_실패_쓰기() {
        // given — staleTimeout보다 과거에 시작해 아직 EXTRACTING인 목표.
        val target = pendingTarget()
        whenever(
            repository.findByStrategyStatusAndStrategyExtractionStartedAtBefore(eq(StrategyStatus.EXTRACTING), any()),
        ).thenReturn(listOf(target))

        // when
        worker.recoverStale()

        // then — 조건부 실패 쓰기(EXTRACTING일 때만 FAILED, 그 사이 재수정됐으면 0행 폐기).
        verify(repository).markStrategyFailed(target.id)
    }

    @Test
    fun poll은_고아_회수_후_PENDING_한건을_처리한다() {
        // given — 고아 1건 + PENDING 1건.
        val orphan = pendingTarget()
        val pending = pendingTarget()
        whenever(
            repository.findByStrategyStatusAndStrategyExtractionStartedAtBefore(eq(StrategyStatus.EXTRACTING), any()),
        ).thenReturn(listOf(orphan))
        whenever(repository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING)).thenReturn(pending)
        whenever(repository.claimStrategyExtraction(any(), any())).thenReturn(1)
        whenever(repository.findById(any())).thenReturn(Optional.of(pending))
        whenever(extractor.extract(any(), any(), any())).thenReturn(StrategyExtraction.Extracted(strategy))

        // when
        worker.poll()

        // then — 고아 실패 + PENDING 결과 쓰기 둘 다.
        verify(repository).markStrategyFailed(orphan.id)
        verify(repository).writeStrategyResult(any(), any())
    }
}
