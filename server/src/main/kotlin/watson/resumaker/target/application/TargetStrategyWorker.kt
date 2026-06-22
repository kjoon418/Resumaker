package watson.resumaker.target.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import watson.resumaker.target.domain.StrategyStatus
import watson.resumaker.target.infrastructure.TargetBriefRepository
import java.time.Clock
import java.time.Instant

/**
 * 작성 전략 추출 워커([watson.resumaker.generation.application.GenerationJobWorker]와 동형). @Scheduled 폴링으로
 * PENDING 목표를 하나씩 집어 채용 방향에서 작성 전략을 추출해 저장한다.
 *
 * **한 틱의 일(poll):**
 * 1. [recoverStale]: 너무 오래 EXTRACTING인(=죽은 워커가 남긴) 고아 목표를 조건부로 FAILED 처리한다(영원히
 *    EXTRACTING 방지). 재추출하지 않는다(사용자 재시도·생성 시 원문 폴백).
 * 2. [claimAndExtractOne]: 가장 오래 기다린 PENDING 1건을 **원자 클레임**([TargetBriefRepository.claimStrategyExtraction])해
 *    EXTRACTING으로 바꾼 뒤 추출한다. claim이 1을 돌려줄 때만(이 호출이 소유) 처리한다. 한 틱에 1건이다.
 *
 * **조건부 결과 쓰기:** 추출은 claim 시점의 채용 방향으로 한다. 결과 쓰기는 **EXTRACTING일 때만** 적용된다
 * ([TargetBriefRepository.writeStrategyResult]). 추출 중 사용자가 채용 방향을 수정해 PENDING으로 돌아갔다면 결과
 * 쓰기가 0행이 되어 폐기되고(다음 틱 재추출), 실패 쓰기도 마찬가지로 EXTRACTING일 때만 FAILED로 전이한다.
 *
 * **트랜잭션 경계:** 외부 LLM 호출([TargetStrategyExtractor.extract])은 트랜잭션 밖에서 일어난다(긴 트랜잭션
 * 금지). claim/결과 쓰기/실패 쓰기는 각자 짧은 @Modifying 영속이다.
 */
@Component
class TargetStrategyWorker(
    private val targetRepository: TargetBriefRepository,
    private val extractor: TargetStrategyExtractor,
    private val properties: TargetStrategyProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    @Scheduled(fixedDelayString = "\${resumaker.target-strategy.poll-interval-ms:2000}")
    fun poll() {
        recoverStale()
        claimAndExtractOne()
    }

    /**
     * 고아 EXTRACTING 회수: strategyExtractionStartedAt이 staleRunningTimeout보다 과거인 EXTRACTING 목표를 조건부로
     * FAILED 처리한다. 워커가 추출 도중 죽으면 목표가 영원히 EXTRACTING으로 남으므로, 시간 초과를 죽음으로 간주한다.
     */
    fun recoverStale() {
        val cutoff = Instant.now(clock).minus(properties.staleRunningTimeout)
        targetRepository.findByStrategyStatusAndStrategyExtractionStartedAtBefore(StrategyStatus.EXTRACTING, cutoff)
            .forEach { stale ->
                // 조건부 실패 쓰기(EXTRACTING일 때만). 그 사이 재수정(PENDING)됐으면 0행이라 폐기된다.
                targetRepository.markStrategyFailed(stale.id)
            }
    }

    /**
     * 가장 오래 기다린 PENDING 1건을 원자 클레임해 추출한다. claim이 1(이 호출이 소유)일 때만 reload 후 추출·결과
     * 쓰기. 0이면 다른 호출이 먼저 가져갔으니 이 틱은 건너뛴다.
     */
    fun claimAndExtractOne() {
        val pending = targetRepository.findFirstByStrategyStatusOrderById(StrategyStatus.PENDING) ?: return
        if (targetRepository.claimStrategyExtraction(pending.id, Instant.now(clock)) != 1) {
            return
        }
        // claim 시점의 채용 방향으로 추출한다(reload로 클레임된 최신 상태 적재).
        val claimed = targetRepository.findById(pending.id.value).orElse(null) ?: return
        when (
            val result = extractor.extract(
                recruitDirection = claimed.recruitDirection.value,
                company = claimed.company?.value,
                job = claimed.job?.value,
            )
        ) {
            is StrategyExtraction.Extracted -> {
                val json = objectMapper.writeValueAsString(result.strategy)
                // 조건부 결과 쓰기(EXTRACTING일 때만). 0행이면 추출 중 사용자가 수정해 PENDING으로 돌아간 것이므로
                // 결과를 폐기한다(다음 틱 재추출).
                targetRepository.writeStrategyResult(claimed.id, json)
            }
            StrategyExtraction.Unavailable -> {
                // 조건부 실패 쓰기(EXTRACTING일 때만). 0행이면 재수정된 것이므로 실패 표시를 폐기한다.
                targetRepository.markStrategyFailed(claimed.id)
            }
        }
    }
}
