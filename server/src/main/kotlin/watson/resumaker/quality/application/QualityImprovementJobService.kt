package watson.resumaker.quality.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.GenerationQuotaGuard
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobId
import watson.resumaker.quality.domain.TreatmentKind
import watson.resumaker.quality.infrastructure.QualityCandidateRepository
import watson.resumaker.quality.infrastructure.QualityImprovementJobRepository
import watson.resumaker.quality.presentation.QualityImprovementJobMapper
import watson.resumaker.quality.presentation.QualityImprovementJobResponse
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 품질 개선 작업의 **접수·조회** 유스케이스([watson.resumaker.generation.application.GenerationJobService] 동형).
 *
 * 접수는 외부 LLM을 기다리지 않는다: 진단을 다시 돌려 요청한 소견이 **현재 활성 버전의 AUTO_REWRITE 소견인지**
 * 검증한 뒤(이력서 가드·소유 격리는 진단이 강제) PENDING 작업을 만들어 즉시 jobId를 돌려준다(컨트롤러가 202).
 * 실제 처치는 [QualityImprovementJobWorker]가 백그라운드에서 수행한다.
 *
 * **AUTO_REWRITE만 접수:** 요청 findingIds 중 진단 결과에서 AUTO_REWRITE인 것만 남긴다(개선 제안·범위 밖은 처치
 * 작업을 거치지 않는다 — §132). 남는 게 0건이면 만들 후보가 없으므로 접수를 거부한다(400).
 *
 * **소유 격리(QC8):** 진단([QualityReviewService.review])이 findByIdAndOwnerId로 강제한다. 단건 조회도 ownerId 조건.
 */
@Service
class QualityImprovementJobService(
    private val reviewService: QualityReviewService,
    private val jobRepository: QualityImprovementJobRepository,
    private val candidateRepository: QualityCandidateRepository,
    private val quotaGuard: GenerationQuotaGuard,
    private val mapper: QualityImprovementJobMapper,
    private val clock: Clock,
) {

    @Transactional
    fun submit(ownerId: UserId, artifactId: UUID, findingIds: List<String>): QualityImprovementJobResponse {
        // 접수 사전 점검(빠른 실패): 별도 일일 한도를 이미 넘었으면 작업을 만들지 않고 즉시 429로 막는다(§5.1-3).
        // 실제 차감은 워커가 후보를 영속한 뒤(작업 성공 시) 한다(QC7 — 전 항목 실패 시 미차감).
        quotaGuard.checkQualityImprovement(ownerId)
        // 진단을 다시 돌려 요청 소견을 검증한다(이력서 가드 QC10·소유 격리 QC8를 review가 강제).
        val report = reviewService.review(ownerId, artifactId)
        val requested = findingIds.toSet()
        val accepted = report.findings
            .filter { it.findingId in requested && it.treatmentKind == TreatmentKind.AUTO_REWRITE }
            .map { it.findingId }

        if (accepted.isEmpty()) {
            // 요청 소견 중 자동 적용 가능한 것이 없다(이미 해소됐거나 개선 제안만 골랐거나 잘못된 식별자).
            throw DomainValidationException("다듬을 수 있는 소견이 없어요. 품질 점검을 다시 해 주세요.")
        }

        val job = QualityImprovementJob.create(
            ownerId = ownerId,
            artifactId = artifactId,
            versionId = report.versionId,
            findingIds = accepted,
            createdAt = Instant.now(clock),
        )
        return mapper.toResponse(jobRepository.save(job), candidates = emptyList())
    }

    @Transactional(readOnly = true)
    fun get(ownerId: UserId, jobId: QualityImprovementJobId): QualityImprovementJobResponse {
        val job = jobRepository.findByIdAndOwnerId(jobId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 품질 개선 작업을 찾을 수 없어요.")
        // 후보는 작업에 jobId로 연결돼 있다(작업 성공 시에만 존재). 조회 시 함께 적재해 비교·채택 정보를 내린다.
        val candidates = candidateRepository.findAllByJobId(job.id.value)
        return mapper.toResponse(job, candidates)
    }

    /**
     * 한 산출물의 가장 최근 품질 개선 작업을 돌려준다(없으면 null → 컨트롤러가 204). 산출물 열람 화면이 비차단
     * 진행 카드를 복원할 때 쓴다(§3 "이대로 다듬기"를 누르면 화면으로 돌아오고, 화면이 이 작업을 폴링한다).
     * 채택 시 작업을 삭제하므로 채택 완료된 작업은 잡히지 않는다(카드 재노출 방지).
     */
    @Transactional(readOnly = true)
    fun latestFor(ownerId: UserId, artifactId: UUID): QualityImprovementJobResponse? {
        val job = jobRepository.findFirstByArtifactIdAndOwnerIdOrderByCreatedAtDesc(artifactId, ownerId)
            ?: return null
        val candidates = candidateRepository.findAllByJobId(job.id.value)
        return mapper.toResponse(job, candidates)
    }

    /**
     * 품질 개선 작업을 닫는다(소비/정리). 진행 카드의 "닫기"(실패·미채택 작업 치우기)에서 호출한다. 작업과 그에
     * 딸린 후보를 함께 지운다(소유 격리 QC8 — 작업이 이 산출물 것이 아니면 404). 채택과 달리 새 버전을 만들지 않는다.
     */
    @Transactional
    fun dismiss(ownerId: UserId, artifactId: UUID, jobId: QualityImprovementJobId) {
        val job = jobRepository.findByIdAndOwnerId(jobId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 품질 개선 작업을 찾을 수 없어요.")
        if (job.artifactId != artifactId) {
            throw ResourceNotFoundException("요청하신 품질 개선 작업을 찾을 수 없어요.")
        }
        candidateRepository.deleteByJobId(job.id.value)
        jobRepository.delete(job)
    }
}
