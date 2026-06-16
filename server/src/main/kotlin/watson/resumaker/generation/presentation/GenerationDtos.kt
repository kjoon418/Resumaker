package watson.resumaker.generation.presentation

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus

/**
 * 1차 생성 결과 응답 DTO(구현 설계 §5 흐름 5 "Response DTO 변환을 트랜잭션 내부에서").
 *
 * 컨트롤러(Cycle D)가 이 DTO를 그대로 반환한다. 부분 실패 버전도 200으로 내려가며(도메인 이해 §306), 실패 항목의
 * 상태가 포함된다. activeVersionId가 곧 방금 저장·활성화된 초기 버전이다(수용 기준 7).
 *
 * 요청 DTO(ResumeGenerationRequest 등)와 컨트롤러는 Cycle D에서 추가한다. 이 사이클은 응답 형태만 고정한다.
 */
data class GenerationResponse(
    val artifactId: String,
    val kind: ArtifactKind,
    val activeVersionId: String,
    val sections: List<GeneratedSectionResponse>,
)

/**
 * 생성된 한 항목의 응답 DTO. status로 성공/실패를 구분한다(GENERATED | GENERATION_FAILED).
 */
data class GeneratedSectionResponse(
    val sectionId: String,
    val definitionKey: String,
    val sectionKind: SectionKind,
    val content: String,
    val status: SectionStatus,
    val sourceExperienceIds: List<String>,
    val factGroundings: List<FactGroundingResponse>,
)

/**
 * 생성 근거 층위2 응답 DTO(사용자 탐색·표시용).
 */
data class FactGroundingResponse(
    val token: String,
    val kind: FactKind,
    val sourceExperienceId: String,
    val evidenceText: String,
)

// ── Cycle D: 생성 요청 DTO ──────────────────────────────────────────────────

/**
 * 이력서 1차 생성 요청 DTO(POST /artifacts/resume). Bean Validation은 필수값(경험 선택·목표·지정 양식)만
 * 검증한다(구현 설계 §9). 빈 경험 묶음은 형식상 누락(400)으로, 생성 단계의 "빈 묶음 거부"(409)와 구분된다.
 *
 * @param experienceIds 생성에 쓸 경험 식별자(하나 이상 필수).
 * @param targetId      목표 정보 식별자(채용 방향 필수).
 * @param templateId    지정 양식 식별자(이번 사이클은 양식 필수 — AI 생성 양식은 다음 사이클).
 */
data class ResumeGenerationRequest(
    @field:NotEmpty(message = "이력서를 만들 경험을 하나 이상 골라 주세요.")
    val experienceIds: List<String>? = null,
    @field:NotNull(message = "목표 정보를 골라 주세요.")
    val targetId: String?,
    @field:NotNull(message = "사용할 이력서 양식을 골라 주세요.")
    val templateId: String?,
)

/**
 * 포트폴리오 1차 생성 요청 DTO(POST /artifacts/portfolio). 포트폴리오는 양식이 없고 선택 경험당 서사 1개를 만든다.
 *
 * @param experienceIds 선택 경험 식별자(하나 이상 필수). 경험당 항목 1개로 1:1 대응.
 * @param targetId      목표 정보 식별자(채용 방향 필수).
 */
data class PortfolioGenerationRequest(
    @field:NotEmpty(message = "포트폴리오를 만들 경험을 하나 이상 골라 주세요.")
    val experienceIds: List<String>? = null,
    @field:NotNull(message = "목표 정보를 골라 주세요.")
    val targetId: String?,
)

// ── Cycle D: 열람 응답 DTO(수용 기준 12) ────────────────────────────────────

/**
 * 산출물 열람 응답 DTO(GET /artifacts/{id}, 수용 기준 12). 활성 버전의 전체/항목 텍스트·상태·항목 출처를
 * 표시용으로 내려준다. 복사는 클라이언트가 이 텍스트로 수행한다(도메인 이해 §6).
 *
 * 지연 로딩 경계를 넘지 않도록 read 서비스의 트랜잭션 내부에서 변환된다(구현 설계 §5).
 */
data class ArtifactResponse(
    val id: String,
    val kind: ArtifactKind,
    val activeVersion: ArtifactVersionResponse,
)

/**
 * 활성 버전 응답 DTO. 버전이 담은 항목 목록(순서 보존)을 표시용으로 내려준다.
 */
data class ArtifactVersionResponse(
    val versionId: String,
    val sections: List<ArtifactSectionResponse>,
)

/**
 * 열람용 항목 응답 DTO. 항목 출처(sourceExperienceIds)는 표시용이며, 상태로 부분 실패를 구분한다.
 */
data class ArtifactSectionResponse(
    val id: String,
    val sectionKind: SectionKind,
    val definitionKey: String,
    val content: String,
    val status: SectionStatus,
    val sourceExperienceIds: List<String>,
)
