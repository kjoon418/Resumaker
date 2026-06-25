package watson.resumaker.quality.domain

import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

/**
 * 품질 진단 결과(품질 개선 기획 §3.1). 한 산출물(활성 버전)을 개선 기준에 비추어 점검한 **읽기 전용** 결과다.
 *
 * 진단은 산출물을 바꾸지 않으며 영속하지도 않는다(기획 §4.3 권장: 진단은 휘발, 요청 시 계산). 위반·약점이 감지된
 * 지점들을 [Finding] 목록으로 담는다. [autoRewriteCount]는 그중 AUTO_REWRITE 처치 소견 수(프론트가 "이대로 다듬기"
 * 버튼 활성/개수 표시에 쓴다 — 0이면 다듬을 후보가 없는 것).
 *
 * @param versionId 진단한 활성 버전(클라이언트가 처치 요청 시 같은 버전 맥락을 잇도록 함께 내린다).
 */
data class QualityReport(
    val artifactId: UUID,
    val versionId: UUID,
    val findings: List<Finding>,
    /**
     * 소견이 달린 항목들(활성 버전 순서, 소견 0건 항목은 제외). 각 항목의 이름·실제 내용을 함께 담아, 클라이언트가
     * "어느 항목의 어느 내용이 문제인지"를 항목별로 묶어 보여줄 수 있게 한다(소견을 평평한 목록으로만 주면 같은
     * 기준이 여러 항목에서 중복처럼 보이는 문제 해소). [findings]는 처치 접수·결정성 검사 호환을 위해 평평하게 유지한다.
     */
    val sections: List<ReviewedSection> = emptyList(),
) {
    /** AUTO_REWRITE 처치 소견 수(비동기 개선 작업으로 다듬을 수 있는 후보의 개수). */
    val autoRewriteCount: Int get() = findings.count { it.treatmentKind == TreatmentKind.AUTO_REWRITE }
}

/**
 * 진단에서 소견이 달린 한 항목의 표시 맥락. 소견을 항목에 정박(anchor)시키기 위한 항목 이름·내용이다.
 *
 * @param sectionId     활성 버전의 항목 식별자(소견 [Finding.sectionId]와 대응 — 클라이언트가 묶음 키로 쓴다).
 * @param definitionKey 항목 이름(열람 화면이 항목 제목으로 쓰는 키 그대로 — 사용자가 알아보는 라벨).
 * @param content       항목의 현재 내용(사용자가 "내 이력서의 이 부분"임을 알아보는 정박점).
 */
data class ReviewedSection(
    val sectionId: SectionId,
    val definitionKey: String,
    val content: String,
)

/**
 * 개선 소견(품질 개선 기획 §3.1). "어느 생성 항목의, 어떤 개선 기준을, 어떻게 위반/약화했는가"의 한 건의 지적.
 *
 * 각 소견은 **처치 종류**([TreatmentKind])를 함께 가진다. AUTO_REWRITE는 비동기 개선 작업으로 후보를 만들고,
 * SUGGESTION은 경험 보강 안내([suggestionGuide])만 노출하며 텍스트를 바꾸지 않는다.
 *
 * @param findingId    이 소견의 식별자(진단 회차 안에서 유일 — 휘발이므로 결정적 파생값으로 둔다).
 * @param sectionId    소견이 가리키는 활성 버전의 생성 항목.
 * @param definitionKey 항목의 섹션 정의 키(버전 간 항목 대응 키 — 처치 결과 매칭에 쓴다).
 * @param criterion    위반/약화한 개선 기준.
 * @param evidenceText 결정적·반자동 검사가 찾은 근거(예: 약한 동사 "담당했다", 버즈워드 "열정적"). 표시용.
 * @param suggestionGuide SUGGESTION일 때의 보강 안내(텍스트 + 대상 경험). AUTO_REWRITE·OUT_OF_SCOPE면 null.
 */
data class Finding(
    val findingId: String,
    val sectionId: SectionId,
    val definitionKey: String,
    val criterion: QualityCriterion,
    val treatmentKind: TreatmentKind,
    val evidenceText: String? = null,
    val suggestionGuide: SuggestionGuide? = null,
)

/**
 * 개선 제안의 보강 안내(품질 개선 기획 §2·§3.6 성공(개선 제안)). 텍스트를 바꾸지 않고 사용자를 경험 기록 보강으로
 * 유도한다. [targetExperienceId]가 있으면 프론트가 "그 경험 보강하러 가기"로 연결한다(없으면 일반 안내).
 */
data class SuggestionGuide(
    val message: String,
    val targetExperienceId: ExperienceRecordId? = null,
)
