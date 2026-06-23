package watson.resumaker.quality.presentation

import jakarta.validation.constraints.NotEmpty
import watson.resumaker.quality.domain.QualityImprovementJobStatus

/**
 * 품질 개선 작업 접수 요청 DTO(POST /artifacts/{artifactId}/quality-improvements). 처치할 AUTO_REWRITE 소견
 * 식별자만 담는다. 빈 목록은 형식 검증으로 거부한다(다듬을 소견을 하나 이상 골라야 함).
 */
data class QualityImprovementRequest(
    @field:NotEmpty(message = "다듬을 소견을 하나 이상 골라 주세요.")
    val findingIds: List<String>? = null,
)

/**
 * 품질 개선 작업 응답 DTO(접수 202·단건 조회 공통, 개발팀장 계약). [status]로 진행/완료/실패를 분기하고,
 * SUCCEEDED면 [candidates]로 원본↔후보 비교·채택을 한다. FAILED면 [errorCode]/[errorMessage]로 안내한다.
 *
 * [candidates]는 SUCCEEDED일 때만 채워진다(그 외 null — 진행 중·실패엔 후보가 없다).
 */
data class QualityImprovementJobResponse(
    val jobId: String,
    val status: QualityImprovementJobStatus,
    val candidates: List<CandidateDto>?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: String,
)

/**
 * 항목 후보 응답 DTO. 프론트가 원본↔후보를 나란히 비교해 항목별/일괄로 채택한다.
 *
 * @param appliedCriterionIds 이 후보가 적용한 개선 기준 식별자(무엇을 다듬었는지 표시용).
 */
data class CandidateDto(
    val candidateId: String,
    val sectionId: String,
    val definitionKey: String,
    val originalContent: String,
    val candidateContent: String,
    val appliedCriterionIds: List<String>,
)

/**
 * 후보 채택 요청 DTO(POST /artifacts/{artifactId}/quality-improvements/{jobId}/adopt). 채택할 후보 식별자만 담는다.
 * 일괄(여러 후보)·부분(일부) 채택 모두 한 번의 버전 전이로 묶인다. 빈 목록은 형식 검증으로 거부한다.
 */
data class AdoptCandidatesRequest(
    @field:NotEmpty(message = "채택할 후보를 하나 이상 골라 주세요.")
    val candidateIds: List<String>? = null,
)
