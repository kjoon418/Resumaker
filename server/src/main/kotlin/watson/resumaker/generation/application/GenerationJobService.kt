package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobId
import watson.resumaker.generation.infrastructure.GenerationJobRepository
import watson.resumaker.generation.presentation.GenerationJobMapper
import watson.resumaker.generation.presentation.GenerationJobResponse
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.target.infrastructure.TargetBriefRepository
import java.time.Clock
import java.time.Instant

/**
 * 비동기 산출물 생성의 **제출·조회·삭제** 유스케이스(동기 생성을 작업 기반으로 전환).
 *
 * 제출은 동기 생성처럼 외부 LLM을 기다리지 않는다: 가드레일 사전 점검 + 목표 존재 검증만 한 뒤 PENDING 작업을
 * 만들어 즉시 jobId를 돌려준다(컨트롤러가 202). 실제 생성은 [GenerationJobWorker]가 백그라운드에서 수행한다.
 *
 * **제출 시 가드레일 사전 점검(빠른 실패):** 상한을 이미 넘었다면 작업을 만들지 않고 즉시 429로 막는다(쓸데없는
 * 대기열 적재 방지). 실제 차감은 워커가 호출하는 [ArtifactGenerationService] 내부 tx2에서 일어난다(워커가 따로
 * 차감하지 않는다 — 제출 시 점검과 워커 내부 점검이 중복되지만 무해하다).
 *
 * **소유 격리:** 모든 조회는 ownerId 조건(findByIdAndOwnerId 등). 타인 소유·미존재는 동일하게 404로 매핑한다.
 */
@Service
class GenerationJobService(
    private val jobRepository: GenerationJobRepository,
    private val targetRepository: TargetBriefRepository,
    private val quotaGuard: GenerationQuotaGuard,
    private val mapper: GenerationJobMapper,
    private val clock: Clock,
) {

    @Transactional
    fun submitResume(ownerId: UserId, command: GenerateResumeCommand): GenerationJobResponse {
        quotaGuard.checkInitialGeneration(ownerId)
        val target = loadTarget(ownerId, command.targetId)
        val job = GenerationJob.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            experienceIds = command.experienceIds.map { it.value },
            targetId = command.targetId.value,
            templateId = command.templateId?.value,
            targetCompany = target.company?.value,
            createdAt = Instant.now(clock),
        )
        return mapper.toResponse(jobRepository.save(job))
    }

    @Transactional
    fun submitPortfolio(ownerId: UserId, command: GeneratePortfolioCommand): GenerationJobResponse {
        quotaGuard.checkInitialGeneration(ownerId)
        val target = loadTarget(ownerId, command.targetId)
        val job = GenerationJob.create(
            ownerId = ownerId,
            kind = ArtifactKind.PORTFOLIO,
            experienceIds = command.experienceIds.map { it.value },
            targetId = command.targetId.value,
            templateId = null,
            targetCompany = target.company?.value,
            createdAt = Instant.now(clock),
        )
        return mapper.toResponse(jobRepository.save(job))
    }

    @Transactional(readOnly = true)
    fun list(ownerId: UserId): List<GenerationJobResponse> =
        jobRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).map { mapper.toResponse(it) }

    @Transactional(readOnly = true)
    fun get(ownerId: UserId, id: GenerationJobId): GenerationJobResponse {
        val job = jobRepository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 생성 작업을 찾을 수 없어요.")
        return mapper.toResponse(job)
    }

    /**
     * 종료된 작업을 삭제한다. 활성(PENDING/RUNNING) 작업은 처리 중이라 삭제할 수 없다(409). 소유 격리·미존재는 404.
     */
    @Transactional
    fun delete(ownerId: UserId, id: GenerationJobId) {
        val job = jobRepository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 생성 작업을 찾을 수 없어요.")
        if (job.status.isActive()) {
            throw ConflictException("생성 중인 작업은 삭제할 수 없어요.")
        }
        jobRepository.delete(job)
    }

    private fun loadTarget(ownerId: UserId, targetId: TargetBriefId): TargetBrief =
        targetRepository.findByIdAndOwnerId(targetId, ownerId)
            ?: throw ResourceNotFoundException("선택한 목표 정보를 찾을 수 없어요.")
}
