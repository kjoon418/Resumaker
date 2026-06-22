package watson.resumaker.target.application

import watson.resumaker.target.domain.WritingStrategy

/**
 * 채용 방향 → AI 작성 전략 추출 포트.
 *
 * 채용 방향 원문(보통 모집 공고 전문)에서 "이 회사에 맞춰 이력서/포트폴리오를 어떻게 작성할지" 전략을 LLM으로
 * 추출한다. 회사명·직무명은 보조 맥락으로 함께 넘긴다(선택).
 *
 * LLM 미연결/실패 시 차단하지 않고 [StrategyExtraction.Unavailable]을 반환한다(graceful 폴백 — 생성은 원문으로
 * 진행 가능). 워커가 이를 받아 FAILED로 표시한다(사용자는 재시도하거나 생성 시 원문 폴백을 쓴다).
 */
interface TargetStrategyExtractor {
    fun extract(recruitDirection: String, company: String?, job: String?): StrategyExtraction
}

/**
 * 추출 결과 sealed 타입.
 * - [Extracted]: 작성 전략이 추출됐다.
 * - [Unavailable]: 추출 불가(LLM 미연결·실패·파싱 불가·핵심 요약 누락).
 */
sealed interface StrategyExtraction {
    data class Extracted(val strategy: WritingStrategy) : StrategyExtraction
    data object Unavailable : StrategyExtraction
}
