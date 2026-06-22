package watson.resumaker.model.dto

import kotlinx.serialization.Serializable
import watson.resumaker.model.type.StrategyStatus

/**
 * 목표 정보 DTO. 서버 `target.presentation.TargetDtos`와 1:1.
 */
@Serializable
data class CreateTargetRequest(
    val recruitDirection: String,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

@Serializable
data class UpdateTargetRequest(
    val recruitDirection: String,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

@Serializable
data class TargetResponse(
    val id: String,
    val recruitDirection: String,
    val companyName: String? = null,
    val jobTitle: String? = null,
    /** AI 작성 전략 추출 상태. PENDING/EXTRACTING=분석 중, READY=[writingStrategy] 렌더, FAILED=재시도. */
    val strategyStatus: StrategyStatus = StrategyStatus.PENDING,
    /** 추출된 작성 전략. [strategyStatus]가 READY가 아니면 null. */
    val writingStrategy: WritingStrategyResponse? = null,
)

/**
 * 목표의 채용 방향에서 추출한 AI 작성 전략(서버 계약과 1:1). 생성은 이 전략을 기반으로 이뤄진다.
 * - [keywords]: 강조할 핵심 역량(태그 칩으로 표시).
 * - [tone]: 권장 어조.
 * - [emphasize]: 강조할 점(글머리).
 * - [avoid]: 피할 점(글머리).
 * - [summary]: 공고 요약.
 */
@Serializable
data class WritingStrategyResponse(
    val keywords: List<String> = emptyList(),
    val tone: String = "",
    val emphasize: List<String> = emptyList(),
    val avoid: List<String> = emptyList(),
    val summary: String = "",
)
