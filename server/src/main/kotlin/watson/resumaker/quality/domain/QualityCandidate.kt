package watson.resumaker.quality.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.common.domain.IdentifierGenerator
import java.util.UUID

/**
 * 타입 안전 품질 개선 후보 식별자. value class로 두고 Hibernate가 내부 UUID로 네이티브 매핑한다.
 */
@JvmInline
value class QualityCandidateId(val value: UUID)

/**
 * 품질 개선 **항목 후보**(품질 개선 기획 §3.1 자동 적용). 처치 어댑터가 한 항목의 원본 텍스트를 사실 불변으로 다듬어
 * 만든 후보로, 신뢰성 검증(QC3) + 원본 사실 토큰 보존 검증(QC4)을 **둘 다** 통과한 것만 영속된다(채택 후보).
 *
 * 채택 전까지는 §401의 항목 후보와 동일한 격이다(버전이 아님). 작업([QualityImprovementJob]) 성공 시 그 작업에
 * [jobId]로 연결돼 함께 영속되고, 채택 단계가 [sectionId]·[candidateContent]로 새 버전을 만든다.
 *
 * **버전 격리:** [originalContent]는 접수 시점 활성 버전의 항목 텍스트(비교 뷰·보존 검증 근거). [appliedCriterionIds]는
 * 이 후보가 적용한 개선 기준 식별자(프론트가 "무엇을 다듬었는지" 표시 — 표시용).
 *
 * 별도 테이블로 두는 이유: 작업 애그리거트가 후보 컬렉션을 직접 소유하면 조회마다 EAGER 로딩이 무거워지고, 채택은
 * 후보 식별자 부분집합만 다루므로 jobId 인덱스 조회가 자연스럽다(GenerationJob이 산출물을 직접 소유하지 않는 것과 동형).
 */
@Entity
@Table(name = "quality_candidates")
class QualityCandidate private constructor(
    @Id
    @Column(name = "id")
    val id: QualityCandidateId,
    @Column(name = "job_id", nullable = false)
    val jobId: UUID,
    @Column(name = "section_id", nullable = false)
    val sectionId: SectionId,
    @Column(name = "definition_key", nullable = false, length = 100)
    val definitionKey: String,
    @Column(name = "original_content", nullable = false, length = 10000)
    val originalContent: String,
    @Column(name = "candidate_content", nullable = false, length = 10000)
    val candidateContent: String,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "quality_candidate_criterion_ids",
        joinColumns = [JoinColumn(name = "quality_candidate_id")],
    )
    @OrderColumn(name = "criterion_id_order")
    @Column(name = "criterion_id", nullable = false, length = 32)
    val appliedCriterionIds: MutableList<String>,
) {

    companion object {
        fun create(
            jobId: UUID,
            sectionId: SectionId,
            definitionKey: String,
            originalContent: String,
            candidateContent: String,
            appliedCriterionIds: List<String>,
        ): QualityCandidate = QualityCandidate(
            id = QualityCandidateId(IdentifierGenerator.newId()),
            jobId = jobId,
            sectionId = sectionId,
            definitionKey = definitionKey,
            originalContent = originalContent,
            candidateContent = candidateContent,
            appliedCriterionIds = appliedCriterionIds.toMutableList(),
        )
    }
}
