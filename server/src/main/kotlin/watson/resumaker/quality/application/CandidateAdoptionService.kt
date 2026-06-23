package watson.resumaker.quality.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.ArtifactReadServiceMapper
import watson.resumaker.generation.infrastructure.ArtifactVersioningProperties
import watson.resumaker.generation.presentation.ArtifactResponse
import watson.resumaker.quality.domain.QualityImprovementJobId
import watson.resumaker.quality.infrastructure.QualityCandidateRepository
import watson.resumaker.quality.infrastructure.QualityImprovementJobRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 후보 **채택** 유스케이스(품질 개선 기획 §3.5, 수용 기준 QC5 — 새 활성 버전·미채택 불변·일괄 1버전).
 *
 * 사용자가 작업의 후보 중 일부/전부를 골라 채택하면, 직전 활성 버전을 복제한 위에 채택 후보들의 내용으로 해당 항목만
 * 교체한 **새 활성 버전**을 만든다(채택하지 않은 항목은 그대로 — §334). **일괄 채택은 [watson.resumaker.artifact.domain.Artifact.adoptSections]로
 * 한 번의 버전 전이로 묶는다**(버전 폭증 방지 — 버전 1개·prune 1회).
 *
 * AI 호출이 없는 순수 동기 영속이다(처치는 이미 작업이 끝냈고, 채택은 검증된 후보를 새 버전으로 옮길 뿐). 그래서
 * 항목 편집([watson.resumaker.generation.application.SectionEditService])처럼 단일 트랜잭션으로 처리한다.
 *
 * **소유 격리(QC8):** 작업·산출물 모두 findByIdAndOwnerId로 ownerId 조건을 강제한다. 타인·미존재는 404.
 * **비용 차감 없음:** 차감은 작업 성공 시점에 이미 1회 일어났다(워커 — 오너 확정 §5.1-3). 채택은 차감하지 않는다.
 */
@Service
class CandidateAdoptionService(
    private val jobRepository: QualityImprovementJobRepository,
    private val candidateRepository: QualityCandidateRepository,
    private val artifactRepository: ArtifactRepository,
    private val mapper: ArtifactReadServiceMapper,
    private val versioningProperties: ArtifactVersioningProperties,
    private val clock: Clock,
) {

    @Transactional
    fun adopt(
        ownerId: UserId,
        artifactId: UUID,
        jobId: QualityImprovementJobId,
        candidateIds: List<String>,
    ): ArtifactResponse {
        if (candidateIds.isEmpty()) {
            throw DomainValidationException("채택할 후보를 하나 이상 골라 주세요.")
        }
        // 작업 소유 격리(QC8). 작업이 이 산출물 것이 아니면 거부(경로·작업 불일치 방지).
        val job = jobRepository.findByIdAndOwnerId(jobId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 품질 개선 작업을 찾을 수 없어요.")
        if (job.artifactId != artifactId) {
            throw ResourceNotFoundException("요청하신 품질 개선 작업을 찾을 수 없어요.")
        }

        // 요청한 후보만 이 작업에서 추린다(다른 작업·미존재 후보는 빠진다 — 소유 격리는 작업 조회가 강제).
        val requested = candidateIds.toSet()
        val candidates = candidateRepository.findAllByJobId(job.id.value)
            .filter { it.id.value.toString() in requested }
        if (candidates.isEmpty()) {
            throw ResourceNotFoundException("채택할 후보를 찾을 수 없어요.")
        }

        val artifact = artifactRepository.findByIdAndOwnerId(ArtifactId(artifactId), ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")

        // 채택 맵: sectionId → 후보 내용. 같은 항목에 후보가 둘이면(이론상 없음) 마지막이 이긴다.
        val adopted = candidates.associate { candidate ->
            candidate.sectionId to SectionContent.of(candidate.candidateContent)
        }

        val now = Instant.now(clock)
        // 일괄 채택을 한 번의 버전 전이로 묶는다(QC5 — 새 활성 버전, 미채택 항목 불변). 접수 시점 활성 버전이 바뀌어
        // 항목이 사라졌으면 adoptSections가 DomainValidationException을 던진다(동시성 범위 밖 케이스 → 400).
        artifact.adoptSections(adopted, now)
        // 보관 상한 정리(재생성·편집과 동형): 새 버전 추가로 상한을 넘으면 같은 tx에서 가장 오래된 비활성 버전부터 정리.
        val pruned = artifact.pruneOldestIfExceeds(versioningProperties.versionRetentionLimit)
        val saved = artifactRepository.save(artifact)
        return mapper.toResponse(saved, prunedVersionCount = pruned.size)
    }
}
