package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.account.domain.UserId

/**
 * [GenerationQuotaGuard]의 Cycle B 기본 구현. 항상 허용한다(no-op).
 *
 * Cycle 6이 실제 카운트·차감·달력일 리셋 구현으로 이 빈을 대체(@Primary)하거나 교체한다.
 */
@Component
class AllowingGenerationQuotaGuard : GenerationQuotaGuard {

    override fun checkInitialGeneration(ownerId: UserId) {
        // Cycle B: 항상 허용. Cycle 6에서 잔여 횟수 점검·초과 거부를 구현한다.
    }
}
