package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.FactGrounding
import watson.resumaker.generation.presentation.FactGroundingResponse
import watson.resumaker.generation.presentation.GeneratedSectionResponse
import watson.resumaker.generation.presentation.GenerationResponse

/**
 * 생성 결과 Service Mapper(구현 설계 §8 "Response DTO 변환은 Service Mapper", §5 흐름 5 "트랜잭션 내부 변환").
 *
 * Artifact 애그리거트의 활성 버전(방금 저장된 초기 버전)을 응답 DTO로 변환한다. JPA 지연 로딩 경계를 넘지 않도록
 * 반드시 트랜잭션 내부에서 호출된다(서비스가 @Transactional 안에서 호출).
 */
@Component
class ArtifactGenerationServiceMapper {

    fun toResponse(artifact: Artifact): GenerationResponse {
        val active = artifact.activeVersion()
        return GenerationResponse(
            artifactId = artifact.id.value.toString(),
            kind = artifact.kind,
            activeVersionId = active.id.value.toString(),
            sections = active.sections.map { toSectionResponse(it) },
        )
    }

    private fun toSectionResponse(section: ArtifactSection): GeneratedSectionResponse =
        GeneratedSectionResponse(
            sectionId = section.id.value.toString(),
            definitionKey = section.definitionKey,
            sectionKind = section.sectionKind,
            content = section.content.value,
            status = section.status,
            sourceExperienceIds = section.sourceExperienceIds.map { it.value.toString() },
            factGroundings = section.factGroundings.map { toGroundingResponse(it) },
        )

    private fun toGroundingResponse(grounding: FactGrounding): FactGroundingResponse =
        FactGroundingResponse(
            token = grounding.token.value,
            kind = grounding.kind,
            sourceExperienceId = grounding.sourceExperienceId.value.toString(),
            evidenceText = grounding.evidenceText,
        )
}
