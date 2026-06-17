package watson.resumaker.generation.application

import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.SectionId

/**
 * [GenerationQuotaGuard]의 항상-허용(no-op) 구현. 실제 카운트·차감·리셋은 [CountingGenerationQuotaGuard]가 한다.
 *
 * 운영 빈은 [CountingGenerationQuotaGuard](@Component)이며, 이 클래스는 **가드 제약을 배제하고 다른 동작을 검증하려는
 * 단위 테스트용 fake**로 둔다(예: 1차 생성·재생성 서비스 테스트가 한도와 무관하게 기존 경로를 통과해야 할 때).
 * 그래서 @Component를 붙이지 않는다(빈 충돌 방지 — 운영 컨텍스트에는 실 구현 하나만 존재).
 */
class AllowingGenerationQuotaGuard : GenerationQuotaGuard {

    override fun checkInitialGeneration(ownerId: UserId) {
        // no-op: 항상 허용.
    }

    override fun recordInitialGeneration(ownerId: UserId) {
        // no-op.
    }

    override fun checkRegeneration(ownerId: UserId, sectionId: SectionId) {
        // no-op: 항상 허용.
    }

    override fun recordRegeneration(ownerId: UserId, sectionId: SectionId) {
        // no-op.
    }
}
