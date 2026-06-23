package watson.resumaker.generation.application

import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.SectionId

/**
 * 비용 가드레일(도메인 이해 §394~401·404, 수용 기준 15, 구현 설계 §5·§7). **Cycle 6에서 실 구현.**
 *
 * **점검(check)과 차감(record)은 별개 작업**이다. 점검은 외부 LLM 호출 전 빠른 실패(상한 도달 시 작업 차단)이고,
 * 차감은 작업이 실제로 성공한 뒤(영속 후, 최소 1항목 성공 시)에만 카운트를 깎는다. 이렇게 나눠야 "전 항목 실패 시
 * 미차감"·"검증실패 자동 재시도 미차감" 같은 도메인 규칙을 정확히 지킬 수 있다.
 *
 * 리셋 경계는 두 한도 모두 **사용자 시간대 달력일(자정)**이다(§396·§397·§278 "내일 이어서" 카피와 일치).
 * 상한 도달 시 [watson.resumaker.common.domain.QuotaExceededException]을 던져 흐름을 막고, 남은 횟수·회복 시점·대안을
 * 안내한다(→ GlobalExceptionHandler가 429로 매핑).
 *
 * **점검의 best-effort 특성(TOCTOU):** 점검(check)과 차감(record) 사이에 원자 잠금이 없으므로, 상한 직전에
 * 동시 N 요청이 모두 점검을 통과한 뒤 각자 차감하면 한도+(동시성 수-1)까지 초과될 수 있다. 비용 가드레일의
 * 목적이 무제한 어뷰징 방지(soft cap)이지 hard cap이 아니므로 도메인상 허용된 트레이드오프다. 차감 카운터
 * 자체는 원자 UPDATE로 정확하다. 엄격한 hard cap이 필요해지면 DB 행 잠금이나 분산 락으로 이 seam을 교체한다.
 *
 * **호출 위치(구현 배선):**
 * - [checkInitialGeneration]: 1차 생성 tx1(재료 적재 트랜잭션, 외부 LLM 호출 전) — [ArtifactGenerationService].
 * - [recordInitialGeneration]: 1차 생성 tx2(영속 후, 최소 1항목 성공 시) — [ArtifactGenerationService].
 * - [checkRegeneration]: 항목 재생성 외부 LLM 호출 전(빠른 실패) — [SectionRegenerationService].
 * - [recordRegeneration]: 항목 재생성 tx2(영속 후, 사용자 요청 재생성 최종 성공 시) — [SectionRegenerationService].
 *   (검증실패 자동 재시도는 프로세서가 가드를 호출하지 않아 구조적으로 미차감 — §397.)
 */
interface GenerationQuotaGuard {

    /** 1차 생성 시작 전 사용자당 잔여 횟수를 점검한다(상한 도달 시 [watson.resumaker.common.domain.QuotaExceededException]). */
    fun checkInitialGeneration(ownerId: UserId)

    /** 1차 생성 성공(최소 1항목)을 사용자당 1회 차감한다. 영속 후 호출한다. */
    fun recordInitialGeneration(ownerId: UserId)

    /** 항목 재생성 시작 전 생성 항목당 잔여 횟수를 점검한다(상한 도달 시 [watson.resumaker.common.domain.QuotaExceededException]). */
    fun checkRegeneration(ownerId: UserId, sectionId: SectionId)

    /** 항목 재생성의 사용자 요청 최종 성공을 생성 항목당 1회 차감한다. 영속 후 호출한다. */
    fun recordRegeneration(ownerId: UserId, sectionId: SectionId)

    /**
     * 품질 개선 접수 전 사용자당 잔여 횟수를 점검한다(품질 개선 기획 §5.1-3 — 항목 재생성 상한과 **별개의** 자체 일일
     * 한도). 상한 도달 시 [watson.resumaker.common.domain.QuotaExceededException].
     */
    fun checkQualityImprovement(ownerId: UserId)

    /**
     * 품질 개선 작업 성공(채택 가능 후보 ≥1)을 사용자당 1회 차감한다(전 항목 실패 시 미차감 — QC7). 후보 영속 후 호출한다.
     */
    fun recordQualityImprovement(ownerId: UserId)
}
