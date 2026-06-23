package watson.resumaker.quality.application

import org.springframework.stereotype.Component
import watson.resumaker.generation.application.FactTokenExtractor

/**
 * 원본 사실 토큰 보존 검증(품질 개선 기획 §3.5·§180·§196, 수용 기준 QC4 — **개선 특유 불변식**).
 *
 * 1차 생성의 신뢰성 검증([watson.resumaker.generation.application.DeterministicGroundingValidator])은 "근거 없는 새
 * 사실 추가 0건"을 막는다(QC3·QC9). 품질 개선은 거기에 더해 "다듬다가 **원본의 사실을 흘리거나 바꾸지 않았는가**"를
 * 본다: 다듬기는 표현만 바꿔야 하므로, 원본 항목에 있던 수치·고유명사 토큰이 후보에도 **모두 보존**돼야 한다.
 *
 * 판정: 원본·후보 텍스트에서 [FactTokenExtractor]로 동일 규칙의 사실 토큰을 추출해 **정규화 집합**으로 비교한다.
 * 원본의 정규화 토큰이 후보 집합에 모두 포함되면 보존(통과). 하나라도 빠지면(누락·변형) 실패. 검증기와 같은 추출
 * 규칙을 공유하므로(추출 일원화), 보존 검증이 검증기보다 느슨해 "흘린 사실"을 놓치는 일이 없다.
 *
 * 순수·결정적이며 외부 호출이 없다(같은 입력 → 같은 결과).
 */
@Component
class FactTokenPreservationValidator(
    private val extractor: FactTokenExtractor,
) {

    /**
     * 후보가 원본의 사실 토큰을 모두 보존하는지 판정한다.
     *
     * @return 보존됐으면 true. 빠진 토큰이 하나라도 있으면 false.
     */
    fun preserves(original: String, candidate: String): Boolean = missingTokens(original, candidate).isEmpty()

    /** 후보에서 누락·변형돼 사라진 원본 사실 토큰의 정규화 값 목록(디버깅·표시용). 비어 있으면 보존. */
    fun missingTokens(original: String, candidate: String): List<String> {
        val originalNormalized = extractor.extract(original).map { it.normalized }.toSet()
        val candidateNormalized = extractor.extract(candidate).map { it.normalized }.toSet()
        return (originalNormalized - candidateNormalized).toList()
    }
}
