package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.generation.presentation.ArtifactResponse
import watson.resumaker.generation.presentation.ArtifactSectionResponse
import watson.resumaker.generation.presentation.ArtifactVersionResponse

/**
 * 열람 응답 Service Mapper(구현 설계 §8 "Response DTO 변환은 Service Mapper", §5 흐름 "트랜잭션 내부 변환").
 *
 * Artifact 애그리거트의 활성 버전을 열람 응답 DTO로 변환한다. JPA 지연 로딩 경계를 넘지 않도록 반드시
 * [ArtifactReadService]의 readOnly 트랜잭션 내부에서 호출된다. 근거(factGroundings)는 표시용 텍스트·상태·출처에
 * 비해 부차적이므로 열람 응답에 포함하지 않는다(복사 가치는 본문·출처로 충족 — 도메인 이해 §6).
 */
@Component
class ArtifactReadServiceMapper {

    fun toResponse(artifact: Artifact): ArtifactResponse {
        val active = artifact.activeVersion()
        return ArtifactResponse(
            id = artifact.id.value.toString(),
            kind = artifact.kind,
            activeVersion = ArtifactVersionResponse(
                versionId = active.id.value.toString(),
                sections = active.sections.map { toSectionResponse(it) },
            ),
        )
    }

    private fun toSectionResponse(section: ArtifactSection): ArtifactSectionResponse =
        ArtifactSectionResponse(
            id = section.id.value.toString(),
            sectionKind = section.sectionKind,
            definitionKey = section.definitionKey,
            content = section.content.value,
            status = section.status,
            sourceExperienceIds = section.sourceExperienceIds.map { it.value.toString() },
        )
}
