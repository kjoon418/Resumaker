package watson.resumaker.model.type

import kotlinx.serialization.Serializable

/**
 * 목표의 AI 작성 전략 추출 상태(서버 enum과 1:1). 직렬화 이름은 서버 enum 상수명과 동일해야 한다(JSON 정합).
 *
 * 목표를 저장하면 백엔드가 채용 방향에서 작성 전략을 비동기로 추출한다.
 * - PENDING: 추출 큐잉(대기). 화면은 "분석 중"으로 표시한다.
 * - EXTRACTING: 추출 진행 중. 화면은 "분석 중"으로 표시한다.
 * - READY: 추출 완료. `writingStrategy`가 채워져 구조화 표시가 가능하다.
 * - FAILED: 추출 실패. 재시도(retry)를 노출한다.
 *
 * PENDING·EXTRACTING은 "활성" 상태로, 상세 화면이 이 상태면 폴링을 유지한다.
 */
@Serializable
enum class StrategyStatus {
    PENDING,
    EXTRACTING,
    READY,
    FAILED,
    ;

    /** 폴링을 계속해야 하는 활성(분석 중) 상태인지(READY/FAILED면 더 폴링하지 않는다). */
    val isActive: Boolean get() = this == PENDING || this == EXTRACTING
}
