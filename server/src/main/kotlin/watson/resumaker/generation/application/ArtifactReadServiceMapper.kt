package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.Version
import watson.resumaker.generation.presentation.ArtifactResponse
import watson.resumaker.generation.presentation.ArtifactSectionResponse
import watson.resumaker.generation.presentation.ArtifactSummaryResponse
import watson.resumaker.generation.presentation.ArtifactVersionResponse
import watson.resumaker.generation.presentation.ArtifactVersionsResponse
import watson.resumaker.generation.presentation.VersionHistoryResponse

/**
 * 열람 응답 Service Mapper(구현 설계 §8 "Response DTO 변환은 Service Mapper", §5 흐름 "트랜잭션 내부 변환").
 *
 * Artifact 애그리거트의 활성 버전을 열람 응답 DTO로 변환한다. JPA 지연 로딩 경계를 넘지 않도록 반드시
 * [ArtifactReadService]의 readOnly 트랜잭션 내부에서 호출된다. 근거(factGroundings)는 표시용 텍스트·상태·출처에
 * 비해 부차적이므로 열람 응답에 포함하지 않는다(복사 가치는 본문·출처로 충족 — 도메인 이해 §6).
 */
@Component
class ArtifactReadServiceMapper {

    /**
     * 활성 버전 응답으로 변환한다. [prunedVersionCount]는 이 응답을 만든 작업에서 보관 상한 정리로 사라진
     * 버전 수다(사전 고지 — §398·§273). 열람·복원처럼 정리가 없는 경로는 기본값 0을 쓴다.
     */
    fun toResponse(artifact: Artifact, prunedVersionCount: Int = 0): ArtifactResponse {
        val active = artifact.activeVersion()
        return ArtifactResponse(
            id = artifact.id.value.toString(),
            kind = artifact.kind,
            activeVersion = ArtifactVersionResponse(
                versionId = active.id.value.toString(),
                sections = active.sections.map { toSectionResponse(it) },
            ),
            prunedVersionCount = prunedVersionCount,
        )
    }

    /**
     * 산출물을 목록 카드용 요약으로 변환한다(GET /artifacts). 회사명은 생성 시점 목표 스냅샷에서 읽는다.
     * createdAt은 가장 오래된 버전(=초기 생성)의 생성 시각, updatedAt은 활성 버전의 생성 시각으로 둔다
     * (재생성·편집·복원으로 활성이 바뀌면 갱신된다). JPA 지연 로딩 경계 안에서 변환된다(readOnly 트랜잭션 내부).
     */
    fun toSummary(artifact: Artifact): ArtifactSummaryResponse = ArtifactSummaryResponse(
        id = artifact.id.value.toString(),
        kind = artifact.kind,
        targetCompany = artifact.targetSnapshot.company,
        createdAt = artifact.versions.minOf { it.createdAt }.toString(),
        updatedAt = artifact.activeVersion().createdAt.toString(),
    )

    /**
     * 한 산출물의 모든 버전을 생성 순서(오래된→최신)로 비교용 응답으로 변환한다(수용 기준 11·12, §363).
     * JPA 지연 로딩 경계를 넘지 않도록 readOnly 트랜잭션 내부에서 호출된다.
     */
    fun toVersionsResponse(artifact: Artifact): ArtifactVersionsResponse {
        val activeVersionId = artifact.activeVersion().id
        return ArtifactVersionsResponse(
            artifactId = artifact.id.value.toString(),
            kind = artifact.kind,
            activeVersionId = activeVersionId.value.toString(),
            versions = artifact.versions
                .sortedBy { it.createdAt }
                .map { toVersionHistoryResponse(it, active = it.id == activeVersionId) },
        )
    }

    private fun toVersionHistoryResponse(version: Version, active: Boolean): VersionHistoryResponse =
        VersionHistoryResponse(
            versionId = version.id.value.toString(),
            active = active,
            createdAt = version.createdAt.toString(),
            sections = version.sections.map { toSectionResponse(it) },
        )

    private fun toSectionResponse(section: ArtifactSection): ArtifactSectionResponse =
        ArtifactSectionResponse(
            id = section.id.value.toString(),
            sectionKind = section.sectionKind,
            definitionKey = section.definitionKey,
            content = section.content.value,
            status = section.status,
            sourceExperienceIds = section.sourceExperienceIds.map { it.value.toString() },
            // AI-13: 빈 항목(주로 GENERATION_FAILED)을 명시해 열람 화면이 보강 고지로 분리 노출하게 한다.
            empty = section.content.value.isBlank(),
        )
}
