package watson.resumaker.generation.presentation

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.artifact.domain.VersionId
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.EditSectionContentCommand
import watson.resumaker.generation.application.GeneratePortfolioCommand
import watson.resumaker.generation.application.GenerateResumeCommand
import watson.resumaker.generation.application.RegenerateSectionCommand
import watson.resumaker.generation.application.RestoreVersionCommand
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.template.domain.ResumeTemplateId
import java.util.UUID

/**
 * 컨트롤러 계층의 원시 값 → VO/Command 변환을 담당한다(구현 설계 §8 계층 협력).
 * Bean Validation을 통과한 뒤이므로 필수값(experienceIds·targetId·templateId)은 non-null로 단정한다.
 *
 * 양식/목표 로딩(소유 격리)은 Service의 책임이므로 여기서는 식별자 VO만 만든다(presentation은 도메인 적재를 모름).
 */
@Component
class GenerationMapper {

    fun toResumeCommand(request: ResumeGenerationRequest): GenerateResumeCommand =
        GenerateResumeCommand(
            experienceIds = request.experienceIds!!.map { ExperienceRecordId(UUID.fromString(it)) },
            targetId = TargetBriefId(UUID.fromString(request.targetId!!)),
            templateId = ResumeTemplateId(UUID.fromString(request.templateId!!)),
        )

    fun toPortfolioCommand(request: PortfolioGenerationRequest): GeneratePortfolioCommand =
        GeneratePortfolioCommand(
            experienceIds = request.experienceIds!!.map { ExperienceRecordId(UUID.fromString(it)) },
            targetId = TargetBriefId(UUID.fromString(request.targetId!!)),
        )

    /**
     * 경로 변수(artifactId·sectionId)와 본문(directive)을 재생성 커맨드로 합친다.
     * 목표는 산출물의 불변 스냅샷에서 읽으므로(§347) 커맨드에 포함하지 않는다.
     * 식별자 형식 오류는 IllegalArgument로 전파되어(UUID 파싱) 전역 핸들러 기본 처리된다.
     */
    fun toRegenerateSectionCommand(
        artifactId: String,
        sectionId: String,
        request: RegenerateSectionRequest,
    ): RegenerateSectionCommand =
        RegenerateSectionCommand(
            artifactId = ArtifactId(UUID.fromString(artifactId)),
            sectionId = SectionId(UUID.fromString(sectionId)),
            directive = request.directive?.takeIf { it.isNotBlank() },
        )

    /**
     * 경로 변수(artifactId·sectionId)와 본문(content)을 직접 편집 커맨드로 합친다.
     * Bean Validation(@NotBlank)을 통과한 뒤이므로 content는 non-null·비공백으로 단정한다.
     * 식별자 형식 오류는 IllegalArgument로 전파되어(UUID 파싱) 전역 핸들러 기본 처리된다.
     */
    fun toEditSectionContentCommand(
        artifactId: String,
        sectionId: String,
        request: EditSectionContentRequest,
    ): EditSectionContentCommand =
        EditSectionContentCommand(
            artifactId = ArtifactId(UUID.fromString(artifactId)),
            sectionId = SectionId(UUID.fromString(sectionId)),
            content = request.content!!,
        )

    /**
     * 경로 변수(artifactId·versionId)를 버전 복원 커맨드로 합친다(도메인 이해 §277·§283).
     * 식별자 형식 오류는 IllegalArgument로 전파되어(UUID 파싱) 전역 핸들러 기본 처리된다.
     */
    fun toRestoreVersionCommand(artifactId: String, versionId: String): RestoreVersionCommand =
        RestoreVersionCommand(
            artifactId = ArtifactId(UUID.fromString(artifactId)),
            versionId = VersionId(UUID.fromString(versionId)),
        )
}
