package watson.resumaker.quality.presentation

import org.springframework.stereotype.Component
import watson.resumaker.quality.domain.Finding
import watson.resumaker.quality.domain.QualityReport

/**
 * 품질 진단 결과 응답 Service Mapper(구현 설계 §8 "Response DTO 변환은 Service Mapper"). 도메인 VO의 식별자를
 * 문자열로 바꿔 응답 DTO로 변환한다. 진단은 휘발 산출물이라 지연 로딩 경계에 민감하지 않다.
 */
@Component
class QualityMapper {

    fun toResponse(report: QualityReport): QualityReviewResponse = QualityReviewResponse(
        artifactId = report.artifactId.toString(),
        versionId = report.versionId.toString(),
        findings = report.findings.map { toDto(it) },
        autoRewriteCount = report.autoRewriteCount,
    )

    private fun toDto(finding: Finding): FindingDto = FindingDto(
        findingId = finding.findingId,
        sectionId = finding.sectionId.value.toString(),
        definitionKey = finding.definitionKey,
        criterionId = finding.criterion.criterionId,
        criterionLabel = finding.criterion.label,
        treatmentKind = finding.treatmentKind,
        evidenceText = finding.evidenceText,
        suggestionGuide = finding.suggestionGuide?.let {
            SuggestionGuideDto(message = it.message, targetExperienceId = it.targetExperienceId?.value?.toString())
        },
    )
}
