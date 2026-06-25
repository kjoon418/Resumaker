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

    fun toResponse(job: QualityImprovementJob, candidates: List<QualityCandidate>): QualityImprovementJobResponse {
        val succeeded = job.status == QualityImprovementJobStatus.SUCCEEDED
        return QualityImprovementJobResponse(
            jobId = job.id.value.toString(),
            status = job.status,
            candidates = if (succeeded) candidates.map { toDto(it) } else null,
            errorCode = job.errorCode,
            errorMessage = job.errorMessage,
            createdAt = job.createdAt.toString(),
            excludedSectionCount = if (succeeded) excludedSectionCount(job, candidates) else 0,
        )
    }

    /**
     * 검증 실패로 제외돼 원본을 유지한 항목 수. 작업의 findingIds는 `{sectionId}:{criterionId}` 형식이므로 서로 다른
     * 항목(sectionId) 수를 세고, 실제 만들어진 후보 수를 뺀다(처치는 항목당 1후보). 음수는 0으로 맞춘다(오고지 금지).
     */
    private fun excludedSectionCount(job: QualityImprovementJob, candidates: List<QualityCandidate>): Int {
        val requestedSections = job.findingIds
            .mapNotNull { findingId ->
                val sep = findingId.lastIndexOf(':')
                if (sep <= 0) null else findingId.substring(0, sep)
            }
            .toSet()
            .size
        return (requestedSections - candidates.size).coerceAtLeast(0)
    }

    private fun toDto(candidate: QualityCandidate): CandidateDto = CandidateDto(
        candidateId = candidate.id.value.toString(),
        sectionId = candidate.sectionId.value.toString(),
        definitionKey = candidate.definitionKey,
        originalContent = candidate.originalContent,
        candidateContent = candidate.candidateContent,
        appliedCriterionIds = candidate.appliedCriterionIds.toList(),
    )
}
