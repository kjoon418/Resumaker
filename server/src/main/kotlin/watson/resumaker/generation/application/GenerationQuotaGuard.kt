package watson.resumaker.generation.application

import watson.resumaker.account.domain.UserId

/**
 * 비용 가드레일 사전 점검 seam(구현 설계 §5 흐름 2, §7, 도메인 이해 §390~405). **Cycle 6 범위.**
 *
 * 이 사이클(B)에서는 인터페이스와 **허용(no-op) 기본 구현**([AllowingGenerationQuotaGuard])만 둔다.
 * 생성 유스케이스가 외부 호출(트랜잭션 밖) 직전에 이 점검을 호출하는 흐름상 위치만 표시하고, Cycle B는 항상 허용한다.
 * Cycle 6이 1차 생성 잔여 횟수(사용자당·사용자 시간대 달력일 리셋)와 차감을 이 자리에서 구현한다.
 *
 * 반쪽 구현 금지: 여기서 카운트·차감·리셋을 흉내 내지 않는다(Cycle 6 책임). seam만 둔다.
 */
interface GenerationQuotaGuard {

    /**
     * 1차 생성을 시작하기 전 잔여 횟수를 점검한다(초과면 도메인 예외를 던져 흐름을 막는다). Cycle B는 항상 통과.
     */
    fun checkInitialGeneration(ownerId: UserId)
}
