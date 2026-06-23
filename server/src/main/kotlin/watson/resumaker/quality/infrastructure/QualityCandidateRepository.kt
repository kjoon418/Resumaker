package watson.resumaker.quality.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.resumaker.quality.domain.QualityCandidate
import java.util.UUID

/**
 * 품질 개선 후보 영속성. 후보는 작업([watson.resumaker.quality.domain.QualityImprovementJob])에 jobId로 연결된다
 * (작업 애그리거트가 직접 소유하지 않음 — 조회 단계에서 jobId로 적재). 소유 격리는 작업 조회가 강제하므로
 * (후보 조회는 항상 검증된 jobId로 좁힌다) 여기서는 jobId 조회만 둔다.
 *
 * 식별자 타입은 UUID다(QualityCandidateId value class @Id → 내부 UUID 등록).
 */
interface QualityCandidateRepository : JpaRepository<QualityCandidate, UUID> {

    fun findAllByJobId(jobId: UUID): List<QualityCandidate>

    fun deleteByJobId(jobId: UUID)
}
