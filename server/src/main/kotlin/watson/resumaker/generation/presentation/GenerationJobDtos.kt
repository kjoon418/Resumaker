package watson.resumaker.generation.presentation

import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.generation.domain.GenerationJobRetryMode
import watson.resumaker.generation.domain.GenerationJobStatus

/**
 * 생성 작업 응답 DTO(비동기 생성 — 제출 202·목록·단건 조회 공통). 클라이언트는 [status]로 진행/완료/실패를
 * 분기하고, SUCCEEDED면 [artifactId]로 산출물을 열람한다. FAILED면 [errorCode]/[errorMessage]로 안내한다.
 *
 * [targetCompany]는 작업 카드 제목용 비정규화 회사명(제출 당시 맥락, null 허용). 시각은 ISO-8601 문자열로 내린다
 * (기존 응답 DTO와 동형 — Instant.toString()).
 *
 * **'다시 만들기' 지원([retryMode]·재사용 입력):** 실패 작업을 어떻게 다시 만들지는 서버가 분류해
 * [retryMode]로 내려준다(클라이언트 재분류 금지 — 단일 책임). [GenerationJobRetryMode.EDIT_INPUTS]일 때
 * 클라이언트가 제작 화면을 프리필하도록 제출 당시 입력([experienceIds]·[targetId]·[templateId])을 함께 싣는다
 * (모두 사용자 소유 데이터, 소유 격리 조회 결과). 활성·성공 작업은 [retryMode]가 [GenerationJobRetryMode.NONE].
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
    // 아래 4개는 항상 매퍼가 채운다(기본값은 테스트 픽스처 편의 — 클라이언트 DTO 기본값과 대칭).
    val retryMode: GenerationJobRetryMode = GenerationJobRetryMode.NONE,
    val experienceIds: List<String> = emptyList(),
    val targetId: String = "",
    val templateId: String? = null,
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
