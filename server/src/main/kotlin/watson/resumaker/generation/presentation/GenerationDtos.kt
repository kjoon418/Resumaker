package watson.resumaker.generation.presentation

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
