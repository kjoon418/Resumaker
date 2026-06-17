package watson.resumaker.generation.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserId
import watson.resumaker.account.infrastructure.UserRepository
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.infrastructure.GenerationQuotaCounter
import watson.resumaker.generation.infrastructure.GenerationQuotaCounterRepository
import watson.resumaker.generation.infrastructure.GenerationQuotaProperties
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 비용 가드레일 실 구현(도메인 이해 §396~401·404, 수용 기준 15). 운영 빈(@Component).
 *
 * **카운트·리셋:** 사용량은 날짜-키 카운터 행([GenerationQuotaCounter])으로 영속한다. "오늘"은 사용자 시간대
 * (User.timeZone) 기준 달력일이므로, 자정을 넘기면 날짜 키가 달라져 행이 없는 상태(=0)에서 다시 시작한다
 * (스케줄러 없는 자동 리셋 — 카운터 엔티티 주석 참조).
 *
 * **1차 생성 합산 한도(§396):** scopeKey `INITIAL:{ownerId}`는 사용자당 단일 카운터이므로 이력서·포트폴리오
 * 1차 생성이 **합산**된다(이력서 2회 + 포트폴리오 3회 = 5회 소진 — 종류별 분리 아님).
 *
 * **점검/차감 분리:** 점검은 당일 사용량 ≥ 상한이면 [QuotaExceededException]으로 막는다(외부 호출 전 빠른 실패).
 * 차감은 작업 성공 후 호출돼 카운터를 1 증가시킨다(원자 UPDATE + 최초 사용 행 삽입, 동시성 안전 — 레포 주석).
 *
 * 한도는 [GenerationQuotaProperties]로 외부화된 수치를 쓴다(카운트 대상·리셋 경계는 코드 고정, 수치만 설정).
 */
@Component
class CountingGenerationQuotaGuard(
    private val userRepository: UserRepository,
    private val counterRepository: GenerationQuotaCounterRepository,
    private val properties: GenerationQuotaProperties,
    private val clock: Clock,
) : GenerationQuotaGuard {

    override fun checkInitialGeneration(ownerId: UserId) {
        val today = todayFor(ownerId)
        val used = currentCount(initialScopeKey(ownerId), today)
        if (used >= properties.dailyInitialGenerationLimit) {
            throw QuotaExceededException(
                message = "오늘 만들 수 있는 이력서·포트폴리오 횟수(${properties.dailyInitialGenerationLimit}회)를 모두 썼어요. " +
                    "내일 다시 이어서 만들 수 있어요. 그동안 기존 산출물을 직접 편집해 보세요.",
                code = GENERATION_QUOTA_EXCEEDED,
                action = ACTION_EDIT_MANUALLY,
            )
        }
    }

    override fun recordInitialGeneration(ownerId: UserId) {
        incrementOrInsert(initialScopeKey(ownerId), todayFor(ownerId))
    }

    override fun checkRegeneration(ownerId: UserId, sectionId: SectionId) {
        val today = todayFor(ownerId)
        val used = currentCount(regenerationScopeKey(sectionId), today)
        if (used >= properties.dailyRegenerationLimitPerSection) {
            throw QuotaExceededException(
                message = "이 항목을 오늘 다시 만들 수 있는 횟수(${properties.dailyRegenerationLimitPerSection}회)를 모두 썼어요. " +
                    "내일 다시 시도하거나, 지금은 이 항목을 직접 편집해 보세요.",
                code = REGENERATION_QUOTA_EXCEEDED,
                action = ACTION_EDIT_MANUALLY,
            )
        }
    }

    override fun recordRegeneration(ownerId: UserId, sectionId: SectionId) {
        incrementOrInsert(regenerationScopeKey(sectionId), todayFor(ownerId))
    }

    /** 사용자 시간대 기준 오늘(달력일). 리셋 경계 계산의 단일 진실. */
    private fun todayFor(ownerId: UserId): LocalDate {
        val user = loadUser(ownerId)
        return Instant.now(clock).atZone(user.timeZone.toZoneId()).toLocalDate()
    }

    private fun loadUser(ownerId: UserId): User =
        userRepository.findById(ownerId.value).orElseThrow {
            // 인증된 요청의 소유자가 존재하지 않는 경우(계정 삭제 등). 존재 노출 최소화로 404 계열로 통일한다.
            ResourceNotFoundException("사용자 정보를 찾을 수 없어요.")
        }

    private fun currentCount(scopeKey: String, date: LocalDate): Int =
        counterRepository.findCountByScopeKeyAndQuotaDate(scopeKey, date) ?: 0

    /**
     * 당일 카운터를 1 증가시킨다. 행이 없으면 최초 사용 행을 삽입하고, 동시 삽입으로 유니크 제약이 깨지면
     * 한 번 더 원자 증가로 합류한다(둘 다 분실 없이 합산 — 카운터 엔티티 동시성 주석).
     *
     * **`saveAndFlush` 사용 이유:** `save`(persist)는 flush를 보장하지 않아 유니크 제약 위반이 TX commit 시점에
     * 표면화되면 catch를 우회해 합류 경로가 깨진다. `saveAndFlush`로 메서드 내부에서 즉시 flush해 제약 위반을
     * catch 시점에 잡히게 보장한다.
     */
    private fun incrementOrInsert(scopeKey: String, date: LocalDate) {
        if (counterRepository.increment(scopeKey, date) > 0) {
            return
        }
        try {
            counterRepository.saveAndFlush(GenerationQuotaCounter.firstUse(scopeKey, date))
        } catch (exception: DataIntegrityViolationException) {
            // 동시 최초 삽입 경합: 다른 트랜잭션이 먼저 행을 만들었다. 그 행에 원자 증가로 합류한다.
            counterRepository.increment(scopeKey, date)
        }
    }

    private fun initialScopeKey(ownerId: UserId): String = "$INITIAL_SCOPE_PREFIX${ownerId.value}"

    private fun regenerationScopeKey(sectionId: SectionId): String = "$REGEN_SCOPE_PREFIX${sectionId.value}"

    companion object {
        private const val INITIAL_SCOPE_PREFIX = "INITIAL:"
        private const val REGEN_SCOPE_PREFIX = "REGEN:"

        /** 1차 생성 한도 초과 에러 코드(클라이언트 분기·안내용). */
        const val GENERATION_QUOTA_EXCEEDED = "GENERATION_QUOTA_EXCEEDED"

        /** 항목 재생성 한도 초과 에러 코드. */
        const val REGENERATION_QUOTA_EXCEEDED = "REGENERATION_QUOTA_EXCEEDED"

        /** 대안 행동 힌트: 직접 편집(§399). */
        const val ACTION_EDIT_MANUALLY = "EDIT_MANUALLY"
    }
}
