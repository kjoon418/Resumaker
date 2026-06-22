package watson.resumaker.generation.presentation

import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.generation.domain.GenerationJobStatus

/**
 * 생성 작업 응답 DTO(비동기 생성 — 제출 202·목록·단건 조회 공통). 클라이언트는 [status]로 진행/완료/실패를
 * 분기하고, SUCCEEDED면 [artifactId]로 산출물을 열람한다. FAILED면 [errorCode]/[errorMessage]로 안내한다.
 *
 * [targetCompany]는 작업 카드 제목용 비정규화 회사명(제출 당시 맥락, null 허용). 시각은 ISO-8601 문자열로 내린다
 * (기존 응답 DTO와 동형 — Instant.toString()).
 */
data class GenerationJobResponse(
    val jobId: String,
    val kind: ArtifactKind,
    val status: GenerationJobStatus,
    val artifactId: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val targetCompany: String?,
    val createdAt: String,
)

/**
 * 산출물 목록 카드 응답 DTO(GET /artifacts). 목록 화면에서 산출물을 카드로 보여주는 데 필요한 최소 정보만 담는다
 * (열람·버전 비교는 단건 조회로). [targetCompany]는 생성 시점 목표 회사명(스냅샷, null 허용), 시각은 ISO-8601 문자열.
 */
data class ArtifactSummaryResponse(
    val id: String,
    val kind: ArtifactKind,
    val targetCompany: String?,
    val createdAt: String,
    val updatedAt: String,
)
