package watson.resumaker.quality.presentation

import org.springframework.stereotype.Component
import watson.resumaker.quality.domain.QualityCandidate
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobStatus

/**
 * 품질 개선 작업 응답 Service Mapper(구현 설계 §8). 엔티티의 식별자·시각을 문자열로 바꿔 응답 DTO로 변환한다.
 * 후보는 SUCCEEDED일 때만 내린다(그 외 null — 진행 중·실패엔 후보가 없다).
 */
@Component
class QualityImprovementJobMapper {

    fun toResponse(job: QualityImprovementJob, candidates: List<QualityCandidate>): QualityImprovementJobResponse =
        QualityImprovementJobResponse(
            jobId = job.id.value.toString(),
            status = job.status,
            candidates = if (job.status == QualityImprovementJobStatus.SUCCEEDED) candidates.map { toDto(it) } else null,
            errorCode = job.errorCode,
            errorMessage = job.errorMessage,
            createdAt = job.createdAt.toString(),
        )

    private fun toDto(candidate: QualityCandidate): CandidateDto = CandidateDto(
        candidateId = candidate.id.value.toString(),
        sectionId = candidate.sectionId.value.toString(),
        definitionKey = candidate.definitionKey,
        originalContent = candidate.originalContent,
        candidateContent = candidate.candidateContent,
        appliedCriterionIds = candidate.appliedCriterionIds.toList(),
    )
}
