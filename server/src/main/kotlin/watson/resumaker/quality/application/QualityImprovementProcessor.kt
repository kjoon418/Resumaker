package watson.resumaker.quality.application

import org.springframework.stereotype.Component
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.generation.application.GroundingValidator

/**
 * 한 항목의 **처치 생성 + 신뢰성 검증(QC3) + 원본 사실 토큰 보존 검증(QC4) + 검증실패 자동 1회 재시도**를 캡슐화한
 * 협력자(품질 개선 기획 §3.5·§4.4, 수용 기준 QC3·QC4·QC9).
 *
 * 처치 어댑터(외부 LLM)가 만든 후보는 "다듬기"라는 의도에도 수치·고유명사를 새로 끼워넣거나(QC3) 원본 사실을
 * 흘릴(QC4) 위험이 구조적으로 있다. 그래서 **두 검증을 모두 통과한 후보만** 채택 후보로 인정한다. 하나라도 실패하면
 * **자동 1회만** 재시도하고, 재시도도 실패하면 그 항목은 제외한다(원본 유지 — §187 회복 정책). 이중 비용을 막기 위해
 * 재시도는 **딱 1회**다(검증실패 자동 재시도는 비용 가드를 호출하지 않아 구조적으로 미차감 — §3.5).
 *
 * 트랜잭션 밖에서 호출된다(포트 호출이 외부 LLM — 긴 트랜잭션 금지).
 */
@Component
class QualityImprovementProcessor(
    private val port: QualityImprovementPort,
    private val groundingValidator: GroundingValidator,
    private val preservationValidator: FactTokenPreservationValidator,
) {

    /**
     * 한 항목을 처치해 **검증을 통과한** 후보를 돌려준다. 통과하지 못하면(생성 실패·QC3 실패·QC4 실패) 자동 1회
     * 재시도 후에도 실패하면 null(제외 — 채택 단계가 원본을 유지).
     */
    fun process(input: QualityImprovementInput): GeneratedSection? {
        val first = attempt(input)
        if (first != null) return first
        // 자동 1회 재시도(딱 한 번 — 이중 비용 금지).
        return attempt(input)
    }

    /** 한 번의 처치 시도: 포트 생성 → 두 검증. 모두 통과하면 후보, 아니면 null. */
    private fun attempt(input: QualityImprovementInput): GeneratedSection? {
        val candidate = port.improve(input) ?: return null
        if (!candidate.succeeded) return null
        // 같은 키여야 채택 단계에서 항목이 대응된다(어댑터가 키를 잘못 내면 제외).
        if (candidate.definitionKey != input.definitionKey) return null

        // QC3: 근거 없는 새 수치·고유명사 추가 0건(1차 생성과 동일한 결정적 검증).
        val grounding = groundingValidator.validate(candidate, input.experiences)
        if (!grounding.valid) return null

        // QC4: 원본 사실 토큰 보존(다듬다 흘린 사실 차단 — 개선 특유 불변식).
        if (!preservationValidator.preserves(input.originalContent, candidate.content)) return null

        return candidate
    }
}
