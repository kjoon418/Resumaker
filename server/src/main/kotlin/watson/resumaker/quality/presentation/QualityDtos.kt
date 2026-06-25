package watson.resumaker.quality.presentation

import watson.resumaker.quality.domain.TreatmentKind

/**
 * 품질 점검(진단) 응답 DTO(품질 개선 기획 §3.6, 개발팀장 API 계약). 진단은 읽기 전용 산출물이라 영속하지 않고
 * 매 요청 계산해 내린다.
 *
 * @param versionId 진단한 활성 버전(클라이언트가 처치 요청 시 같은 버전 맥락을 잇는다).
 * @param autoRewriteCount AUTO_REWRITE 소견 수("이대로 다듬기" 버튼 활성/개수 표시용 — 0이면 다듬을 후보 없음).
 */
data class QualityReviewResponse(
    val artifactId: String,
    val versionId: String,
    val findings: List<FindingDto>,
    /**
     * 소견이 달린 항목들(이름·내용). 클라이언트가 소견을 [FindingDto.sectionId] 기준으로 이 항목에 묶어, 항목 이름과
     * 실제 내용 위에서 "어느 부분이 어떻게 문제인지"를 보여준다(같은 기준이 여러 항목에 걸쳐 중복처럼 보이는 문제 해소).
     */
    val sections: List<ReviewedSectionDto>,
    val autoRewriteCount: Int,
)

/** 소견이 달린 한 항목의 표시 맥락(항목 이름 + 현재 내용). 소견을 이 항목에 정박시키는 데 쓴다. */
data class ReviewedSectionDto(
    val sectionId: String,
    val definitionKey: String,
    val content: String,
)

/**
 * 개선 소견 응답 DTO. 프론트는 [treatmentKind]로 시각을 분기한다(§243): AUTO_REWRITE는 "이대로 다듬기"(원본↔후보
 * 비교·채택), SUGGESTION은 "경험 보강하러 가기"(채택 버튼 대신 안내), OUT_OF_SCOPE는 고지만.
 *
 * @param criterionId    위반 기준 식별자(I1·C2 등 — 클라이언트 분류·아이콘용).
 * @param criterionLabel 사람이 읽는 친근한 라벨.
 * @param evidenceText   결정적·반자동 검사가 찾은 근거(약한 동사·버즈워드 등). 표시용, 없으면 null.
 * @param suggestionGuide SUGGESTION일 때의 보강 안내(텍스트 + 대상 경험 식별자). 그 외 null.
 */
data class FindingDto(
    val findingId: String,
    val sectionId: String,
    val definitionKey: String,
    val criterionId: String,
    val criterionLabel: String,
    val treatmentKind: TreatmentKind,
    val evidenceText: String?,
    val suggestionGuide: SuggestionGuideDto?,
)

/** 개선 제안의 보강 안내 응답 DTO. [targetExperienceId]가 있으면 프론트가 그 경험으로 이동을 연결한다. */
data class SuggestionGuideDto(
    val message: String,
    val targetExperienceId: String?,
)
