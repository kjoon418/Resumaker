package watson.resumaker.target.presentation

import jakarta.validation.constraints.NotBlank
import watson.resumaker.target.domain.StrategyStatus

/**
 * 목표 정보 생성 요청 DTO. Bean Validation은 필수값(채용 방향) null/blank만 검증한다(검증 가이드).
 * 회사명·직무명은 선택값.
 */
data class CreateTargetRequest(
    @field:NotBlank(message = "어떤 회사·직무를 겨냥하는지 알려주세요. 공고 내용을 붙여넣어도 좋아요.")
    val recruitDirection: String?,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

/**
 * 목표 정보 수정 요청 DTO.
 */
data class UpdateTargetRequest(
    @field:NotBlank(message = "어떤 회사·직무를 겨냥하는지 알려주세요. 공고 내용을 붙여넣어도 좋아요.")
    val recruitDirection: String?,
    val companyName: String? = null,
    val jobTitle: String? = null,
)

/**
 * 목표 정보 응답 DTO.
 *
 * [writingStrategy]는 채용 방향에서 추출한 AI 작성 전략(구조화). 추출 전/실패/JSON 손상 시 null이다(상태는
 * [strategyStatus]로 별도 전달 — 폴링 대상). 클라이언트는 strategyStatus로 진행 상태를 표시하고, READY일 때
 * writingStrategy를 보여준다.
 */
data class TargetResponse(
    val id: String,
    val recruitDirection: String,
    val companyName: String?,
    val jobTitle: String?,
    val writingStrategy: WritingStrategyResponse?,
    val strategyStatus: StrategyStatus,
)

/**
 * AI 작성 전략 응답 DTO(구조화). 도메인 VO [watson.resumaker.target.domain.WritingStrategy]를 그대로 노출한다.
 */
data class WritingStrategyResponse(
    val keywords: List<String>,
    val tone: String,
    val emphasize: List<String>,
    val avoid: List<String>,
    val summary: String,
)
