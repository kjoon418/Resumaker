package watson.resumaker.model.dto

import kotlinx.serialization.Serializable
import watson.resumaker.model.type.TreatmentKind

/**
 * 품질 점검·개선 DTO. 서버 quality.presentation 계층과 1:1.
 *
 * 흐름:
 *  1. POST /artifacts/{id}/quality-review  → QualityReviewResponse (소견 목록, 동기)
 *  2. POST /artifacts/{id}/quality-improvements { findingIds } → QualityImprovementJobResponse (202, 자동 적용만)
 *  3. GET  /artifacts/{id}/quality-improvements/{jobId}       → QualityImprovementJobResponse (폴링)
 *  4. POST /artifacts/{id}/quality-improvements/{jobId}/adopt { candidateIds } → ArtifactResponse (채택)
 *
 * 포트폴리오 산출물은 진입점을 노출하지 않는다(QC10).
 */

/** 품질 점검 요청 본문 없음 — 경로 변수만으로 충분하므로 빈 객체를 쓴다. */
@Serializable
class QualityReviewRequest

/**
 * 품질 진단 결과 응답(POST /artifacts/{id}/quality-review → 200).
 * [findings] 목록을 친근한 한국어 [FindingDto.criterionLabel]로 표시한다.
 */
@Serializable
data class QualityReviewResponse(
    val artifactId: String,
    val versionId: String,
    val findings: List<FindingDto>,
    /** 자동 적용 처치가 가능한 소견 수(화면이 "이대로 다듬기" 진입 조건 판단용). */
    val autoRewriteCount: Int,
)

/**
 * 개선 소견 한 건.
 * [treatmentKind]로 처치 분기:
 *  - AUTO_REWRITE  : "이대로 다듬기" 체크 대상
 *  - SUGGESTION    : "경험 보강하러 가기" (텍스트 변경 없음)
 *  - OUT_OF_SCOPE  : 노출 안 함 (R1/AP14 서식·접근성 등)
 */
@Serializable
data class FindingDto(
    val findingId: String,
    val sectionId: String,
    val definitionKey: String,
    val criterionId: String,
    /** 친근한 한국어 기준 라벨 — 그대로 표시한다. 예: "강한 동사로 바꾸면 좋아요" */
    val criterionLabel: String,
    val treatmentKind: TreatmentKind,
    /** 근거 텍스트(반자동/자동 점검이 근거를 제공할 때). null이면 기준 라벨만 표시. */
    val evidenceText: String? = null,
    /** 개선 제안 안내(SUGGESTION일 때만). */
    val suggestionGuide: SuggestionGuideDto? = null,
)

/** 개선 제안의 안내 — "경험 보강하러 가기" 버튼 연결 정보. */
@Serializable
data class SuggestionGuideDto(
    /** 사용자에게 보여줄 안내 문구. 예: "이 경험에 구체 수치를 추가하면 더 강해져요." */
    val message: String,
    /** non-null이면 해당 경험 수정 화면으로 이동한다. null이면 경험 목록으로 이동. */
    val targetExperienceId: String? = null,
)

/** 처치 접수 요청(POST /artifacts/{id}/quality-improvements). AUTO_REWRITE 소견 id만 포함. */
@Serializable
data class QualityImprovementRequest(
    val findingIds: List<String>,
)

/**
 * 품질 개선 작업 응답(202 접수 / GET 폴링 / adopt 채택 전후).
 * [status]가 SUCCEEDED일 때만 [candidates]가 채워진다.
 * FAILED면 [errorCode]/[errorMessage]로 실패 원인을 표면화한다(가짜 성공 금지).
 */
@Serializable
data class QualityImprovementJobResponse(
    val jobId: String,
    val status: QualityJobStatus,
    val candidates: List<CandidateDto>? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: String,
)

/** 품질 개선 작업 상태. 기존 GenerationJobStatus와 동형. */
@Serializable
enum class QualityJobStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED;

    val isActive: Boolean get() = this == PENDING || this == RUNNING
}

/**
 * 항목 개선 후보 한 건.
 * [originalContent]↔[candidateContent] 비교 뷰에 사용한다.
 * 채택 시 [candidateId]를 adopt 요청에 포함한다.
 */
@Serializable
data class CandidateDto(
    val candidateId: String,
    val sectionId: String,
    val definitionKey: String,
    val originalContent: String,
    val candidateContent: String,
    /** 이 후보가 처치한 기준 id 목록. */
    val appliedCriterionIds: List<String>,
)

/** 채택 요청(POST /artifacts/{id}/quality-improvements/{jobId}/adopt). */
@Serializable
data class AdoptCandidatesRequest(
    val candidateIds: List<String>,
)
