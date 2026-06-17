package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.Credential
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserId
import watson.resumaker.account.domain.UserTimeZone
import watson.resumaker.account.infrastructure.UserRepository
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.generation.infrastructure.GenerationQuotaCounter
import watson.resumaker.generation.infrastructure.GenerationQuotaCounterRepository
import watson.resumaker.generation.infrastructure.GenerationQuotaProperties
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * [CountingGenerationQuotaGuard] 단위 테스트(비용 가드레일, 수용 기준 15). 결정성을 위해 **fixed Clock + 고정 시간대**.
 *
 * 검증: 상한 도달 시 점검 차단(429 도메인 예외), 미만이면 통과, 차감의 원자 증가/최초 행 삽입,
 * 사용자 시간대 달력일 경계로 "오늘" 계산(자정 넘기면 다음 날 0회), 1차 생성/항목 재생성 분리 카운트.
 */
class CountingGenerationQuotaGuardTest {

    private val userRepository: UserRepository = mock()
    private val counterRepository: GenerationQuotaCounterRepository = mock()
    private val properties = GenerationQuotaProperties(
        dailyInitialGenerationLimit = 10,
        dailyRegenerationLimitPerSection = 5,
    )

    private val ownerId = UserId(UUID.randomUUID())
    private val sectionId = SectionId(UUID.randomUUID())

    /** UTC 09:00 — 서울(+09:00)에서는 같은 날 18:00(달력일 동일), UTC 16:00 — 서울에서는 다음 날 01:00(달력일 +1). */
    private fun guardAt(instant: Instant, zone: String = "Asia/Seoul"): CountingGenerationQuotaGuard {
        whenever(userRepository.findById(ownerId.value)).thenReturn(Optional.of(userWithZone(zone)))
        return CountingGenerationQuotaGuard(
            userRepository = userRepository,
            counterRepository = counterRepository,
            properties = properties,
            clock = Clock.fixed(instant, ZoneOffset.UTC),
        )
    }

    private fun userWithZone(zone: String): User =
        User.retrieve(
            id = ownerId,
            credential = Credential.of("user@example.com", "hashed-password"),
            timeZone = UserTimeZone(zone),
        )

    @Test
    fun 당일_1차생성_사용량이_상한_미만이면_점검을_통과한다() {
        // given — 오늘 9회 사용, 상한 10.
        whenever(counterRepository.findCountByScopeKeyAndQuotaDate(any(), any())).thenReturn(9)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when and then — 통과(예외 없음).
        assertThatCode { guard.checkInitialGeneration(ownerId) }.doesNotThrowAnyException()
    }

    @Test
    fun 당일_1차생성_사용량이_상한에_도달하면_차단된다() {
        // given — 오늘 10회 사용(상한 도달).
        whenever(counterRepository.findCountByScopeKeyAndQuotaDate(any(), any())).thenReturn(10)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when and then — QuotaExceededException(→429), code·대안 안내.
        assertThatThrownBy { guard.checkInitialGeneration(ownerId) }
            .isInstanceOf(QuotaExceededException::class.java)
            .extracting("code").isEqualTo(CountingGenerationQuotaGuard.GENERATION_QUOTA_EXCEEDED)
    }

    @Test
    fun 사용_행이_없으면_0회로_보고_통과한다() {
        // given — 당일 행 없음(null = 0). 새 날·첫 사용 모두 이 경로.
        whenever(counterRepository.findCountByScopeKeyAndQuotaDate(any(), any())).thenReturn(null)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when and then
        assertThatCode { guard.checkInitialGeneration(ownerId) }.doesNotThrowAnyException()
    }

    @Test
    fun 리셋_경계는_사용자_시간대_달력일이다() {
        // given — 서울(+09:00) 자정 직후(UTC 2026-06-16T15:30 = 서울 2026-06-17T00:30). "오늘"은 서울 기준 6/17.
        whenever(counterRepository.findCountByScopeKeyAndQuotaDate(any(), eq(LocalDate.parse("2026-06-17"))))
            .thenReturn(0)
        val guard = guardAt(Instant.parse("2026-06-16T15:30:00Z"))

        // when — 점검 시 서울 달력일(6/17)을 키로 조회해야 한다(전날 6/16 키가 아님 = 자정 리셋).
        guard.checkInitialGeneration(ownerId)

        // then
        verify(counterRepository).findCountByScopeKeyAndQuotaDate(any(), eq(LocalDate.parse("2026-06-17")))
    }

    @Test
    fun 일차생성_차감은_당일_카운터를_원자적으로_증가시킨다() {
        // given — 기존 행 존재(원자 증가 1행 성공).
        whenever(counterRepository.increment(any(), any())).thenReturn(1)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when
        guard.recordInitialGeneration(ownerId)

        // then — 원자 증가만 호출, 최초 행 삽입(saveAndFlush) 미발생.
        verify(counterRepository).increment(any(), eq(LocalDate.parse("2026-06-17")))
        verify(counterRepository, never()).saveAndFlush(any())
    }

    @Test
    fun 첫_사용이면_차감_시_최초_사용_행을_삽입한다() {
        // given — 당일 행 없음(원자 증가가 0행 갱신) → 최초 사용 행 삽입.
        whenever(counterRepository.increment(any(), any())).thenReturn(0)
        whenever(counterRepository.saveAndFlush(any<GenerationQuotaCounter>())).thenAnswer { it.arguments[0] }
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when
        guard.recordInitialGeneration(ownerId)

        // then — 최초 사용 행(count=1) 삽입.
        verify(counterRepository).saveAndFlush(any<GenerationQuotaCounter>())
    }

    @Test
    fun 재생성_한도는_항목당_카운트하며_상한_도달_시_차단된다() {
        // given — 이 항목 오늘 5회(상한 5 도달).
        whenever(counterRepository.findCountByScopeKeyAndQuotaDate(any(), any())).thenReturn(5)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when and then
        assertThatThrownBy { guard.checkRegeneration(ownerId, sectionId) }
            .isInstanceOf(QuotaExceededException::class.java)
            .extracting("code").isEqualTo(CountingGenerationQuotaGuard.REGENERATION_QUOTA_EXCEEDED)
    }

    @Test
    fun 재생성_점검은_항목_식별자를_스코프로_구분한다() {
        // given — 항목별로 다른 스코프 키를 써야 다른 항목의 사용량이 섞이지 않는다.
        whenever(counterRepository.findCountByScopeKeyAndQuotaDate(any(), any())).thenReturn(0)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when
        guard.checkRegeneration(ownerId, sectionId)

        // then — sectionId가 포함된 스코프 키로 조회한다(REGEN: 접두 + sectionId).
        verify(counterRepository).findCountByScopeKeyAndQuotaDate(
            org.mockito.kotlin.argThat { contains(sectionId.value.toString()) },
            any(),
        )
    }

    @Test
    fun 재생성_차감도_항목당_원자_증가로_수행된다() {
        // given
        whenever(counterRepository.increment(any(), any())).thenReturn(1)
        val guard = guardAt(Instant.parse("2026-06-17T03:00:00Z"))

        // when
        guard.recordRegeneration(ownerId, sectionId)

        // then
        verify(counterRepository).increment(
            org.mockito.kotlin.argThat { contains(sectionId.value.toString()) },
            eq(LocalDate.parse("2026-06-17")),
        )
    }
}
