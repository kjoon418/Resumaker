package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobId
import watson.resumaker.generation.domain.GenerationJobRetryMode
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

    /**
     * 일시적 실패 작업을 **저장된 입력 그대로** 다시 만든다(목록의 '다시 만들기' — IN_PLACE). 실패 작업이 보관한
     * 경험·목표·양식으로 새 PENDING 작업을 만들고, 기존 실패 작업은 삭제해 한 트랜잭션에서 교체한다(재사용 +
     * 실패 기록 제거를 동시에 — 사용자가 다시 만들었음을 명확히 인지).
     *
     * **가드:** IN_PLACE가 아닌 작업(입력 오류·한도 초과·활성·성공)은 같은 입력 재요청이 무의미하므로 409로 막는다
     * (입력 오류는 클라이언트가 제작 화면으로 보내고, 한도 초과는 버튼 자체가 없다). 제출과 동일하게 가드레일을
     * 사전 점검(429)하고 목표 존재를 재검증(404)한다.
     */
    @Transactional
    fun retryInPlace(ownerId: UserId, id: GenerationJobId): GenerationJobResponse {
        val failed = jobRepository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 생성 작업을 찾을 수 없어요.")
        if (failed.retryMode() != GenerationJobRetryMode.IN_PLACE) {
            throw ConflictException("이 작업은 같은 정보로 다시 만들 수 없어요. 입력을 바꿔 새로 만들어 주세요.")
        }
        quotaGuard.checkInitialGeneration(ownerId)
        val target = loadTarget(ownerId, TargetBriefId(failed.targetId))
        val retried = GenerationJob.create(
            ownerId = ownerId,
            kind = failed.kind,
            experienceIds = failed.experienceIds.toList(),
            targetId = failed.targetId,
            templateId = failed.templateId,
            targetCompany = target.company?.value,
            createdAt = Instant.now(clock),
        )
        val saved = jobRepository.save(retried)
        jobRepository.delete(failed)
        return mapper.toResponse(saved)
    }

    private fun loadTarget(ownerId: UserId, targetId: TargetBriefId): TargetBrief =
        targetRepository.findByIdAndOwnerId(targetId, ownerId)
            ?: throw ResourceNotFoundException("선택한 목표 정보를 찾을 수 없어요.")
}
