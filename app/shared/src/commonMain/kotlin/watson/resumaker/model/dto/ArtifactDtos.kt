package watson.resumaker.model.dto

import kotlinx.serialization.Serializable
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.FactKind
import watson.resumaker.model.type.SectionKind
import watson.resumaker.model.type.SectionStatus
import watson.resumaker.model.type.TemplateOrigin

/**
 * 산출물 생성/열람 DTO. 서버 `generation.presentation.GenerationDtos`와 1:1.
 *
 * 부분 실패 버전도 정상 응답(생성 200, 모두 성공 201)으로 내려오며(도메인 이해 §306), 항목별 [SectionStatus]로
 * 성공/실패를 구분한다. 클라이언트는 가짜 성공을 만들지 않고 항목 상태를 그대로 고지한다(신뢰성 가드레일).
 *
 * Slice 2(항목 재생성·직접 편집) 요청 DTO는 아래에 추가한다(응답은 [ArtifactResponse] 재사용).
 * Slice 3(버전 목록·복원)은 [ArtifactVersionsResponse]·[VersionHistoryResponse]를 추가한다(복원 응답은
 * [ArtifactResponse] 재사용, 복원 요청은 경로 변수만이라 본문 DTO 없음).
 */

/**
 * 이력서 1차 생성 요청(POST /artifacts/resume). 경험·목표는 필수, 양식은 **선택**(서버 §178·§446).
 * [templateId]가 null이면 AI가 경험·목표 기반으로 양식(섹션 구조)을 생성해 산출물 스냅샷으로 보존한다.
 */
@Serializable
data class ResumeGenerationRequest(
    val experienceIds: List<String>,
    val targetId: String,
    val templateId: String? = null,
)

/**
 * 포트폴리오 1차 생성 요청(POST /artifacts/portfolio). 양식 없음, 선택 경험당 서사 1개.
 */
@Serializable
data class PortfolioGenerationRequest(
    val experienceIds: List<String>,
    val targetId: String,
)

/**
 * 항목 단위 재생성 요청(POST /artifacts/{artifactId}/sections/{sectionId}/regenerate). 서버 RegenerateSectionRequest와 1:1.
 * 산출물·항목 식별자는 경로 변수로 전달하고, 본문에는 선택적 개선 지시만 담는다(빈/null 허용 — §364: 목표는
 * 산출물 스냅샷에서 읽으므로 본문에 목표를 넣지 않는다).
 *
 * @param directive 선택적 개선 지시("더 짧게"·"성과 수치 강조" 등). 없으면 null.
 */
@Serializable
data class RegenerateSectionRequest(
    val directive: String? = null,
)

/**
 * 항목 직접 편집 요청(PUT /artifacts/{artifactId}/sections/{sectionId}/content). 서버 EditSectionContentRequest와 1:1.
 * 산출물·항목 식별자는 경로 변수로 전달하고, 본문에는 사용자가 직접 작성한 내용만 담는다.
 *
 * 직접 편집에는 자동 검증을 적용하지 않으므로(§428) 검증을 통과하지 못할 내용도 그대로 저장된다. 다만 빈 내용은
 * 서버가 400으로 거부하므로 클라이언트도 빈 입력 시 저장을 막는다(인라인 검증 — UX 에러 가이드).
 *
 * @param content 사용자가 직접 작성한 항목 내용(필수·비어 있을 수 없음).
 */
@Serializable
data class EditSectionContentRequest(
    val content: String,
)

/**
 * 1차 생성 결과 응답(POST /artifacts/resume·portfolio). 방금 저장·활성화된 초기 버전 항목들을 담는다.
 * [activeVersionId]가 곧 생성된 활성 버전이다.
 *
 * [templateOrigin]은 양식 출처 신호(서버 §187). [TemplateOrigin.AI_FALLBACK_DEFAULT]이면 화면이 폴백 고지를
 * 표시한다("AI가 양식을 만들지 못해 기본 구조로 만들었어요"). 기본값 [TemplateOrigin.NONE].
 */
@Serializable
data class GenerationResponse(
    val artifactId: String,
    val kind: ArtifactKind,
    val activeVersionId: String,
    val sections: List<GeneratedSectionResponse>,
    val templateOrigin: TemplateOrigin = TemplateOrigin.NONE,
)

/**
 * 생성된 한 항목의 응답. status로 성공/실패(GENERATED | *_FAILED)를 구분한다.
 */
@Serializable
data class GeneratedSectionResponse(
    val sectionId: String,
    val definitionKey: String,
    val sectionKind: SectionKind,
    val content: String,
    val status: SectionStatus,
    val sourceExperienceIds: List<String> = emptyList(),
    val factGroundings: List<FactGroundingResponse> = emptyList(),
)

/**
 * 생성 근거 층위2 응답(사용자 탐색·표시용).
 */
@Serializable
data class FactGroundingResponse(
    val token: String,
    val kind: FactKind,
    val sourceExperienceId: String,
    val evidenceText: String,
)

/**
 * 산출물 열람 응답(GET /artifacts/{id}). 활성 버전의 전체/항목 텍스트·상태·출처를 표시용으로 내려준다.
 * 복사는 클라이언트가 이 텍스트로 수행한다(도메인 이해 §6).
 */
@Serializable
data class ArtifactResponse(
    val id: String,
    val kind: ArtifactKind,
    val activeVersion: ArtifactVersionResponse,
    val prunedVersionCount: Int = 0,
)

/**
 * 활성 버전 응답. 버전이 담은 항목 목록(순서 보존)을 표시용으로 내려준다.
 */
@Serializable
data class ArtifactVersionResponse(
    val versionId: String,
    val sections: List<ArtifactSectionResponse>,
)

/**
 * 버전 목록 조회 응답(GET /artifacts/{artifactId}/versions). 서버 ArtifactVersionsResponse와 1:1.
 * 한 산출물의 **모든 버전**을 생성 순서(오래된→최신)로 내려준다. 별도 비교 엔드포인트가 없으므로 클라이언트가
 * [VersionHistoryResponse.sections]의 definitionKey로 버전 간 '같은 항목'을 맞춰 비교한다(§363). 활성 버전은
 * [activeVersionId]로 가린다.
 */
@Serializable
data class ArtifactVersionsResponse(
    val artifactId: String,
    val kind: ArtifactKind,
    val activeVersionId: String,
    val versions: List<VersionHistoryResponse>,
)

/**
 * 버전 목록의 한 버전 응답. 비교용으로 버전의 모든 항목·활성 여부·생성시각(ISO 문자열)을 담는다.
 * 근거(factGroundings)는 비교·복원 가치에 비해 부차적이라 포함하지 않는다(서버와 동형 — §6).
 */
@Serializable
data class VersionHistoryResponse(
    val versionId: String,
    val active: Boolean,
    val createdAt: String,
    val sections: List<ArtifactSectionResponse>,
)

/**
 * 열람용 항목 응답. 항목 출처(sourceExperienceIds)는 표시용이며, 상태로 부분 실패를 구분한다.
 */
@Serializable
data class ArtifactSectionResponse(
    val id: String,
    val sectionKind: SectionKind,
    val definitionKey: String,
    val content: String,
    val status: SectionStatus,
    val sourceExperienceIds: List<String> = emptyList(),
)
