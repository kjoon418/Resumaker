package watson.resumaker.generation.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.GenerationUnavailableException
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.domain.GenerationErrorCode
import watson.resumaker.generation.domain.GenerationJob
import watson.resumaker.generation.domain.GenerationJobStatus
import watson.resumaker.generation.infrastructure.ClaudeCliException
import watson.resumaker.generation.infrastructure.GenerationJobRepository
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.template.domain.ResumeTemplateId
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 비동기 생성 작업 워커(동기 생성을 백그라운드로 옮긴 핵심). @Scheduled 폴링으로 대기열을 처리한다.
 *
 * **한 틱의 일(poll):**
 * 1. [recoverStale]: 너무 오래 RUNNING인(=죽은 워커가 남긴) 고아 작업을 FAILED로 회수한다(영원히 RUNNING 방지).
 *    재시도하지 않는다 — 생성 파이프라인이 가드레일을 차감하므로 재시도는 이중 비용·이중 차감을 부른다.
 * 2. [claimAndProcessOne]: 가장 오래된 PENDING 1건을 **원자 클레임**([GenerationJobRepository.claim])해 RUNNING으로
 *    바꾼 뒤 처리한다. claim이 1을 돌려줄 때만(이 호출이 소유) 처리한다. 한 틱에 1건 — fixedDelay라 처리가 끝난
 *    뒤 다음 틱이 다음 작업을 집는다(MVP 단일 동시성으로 충분).
 *
 * **트랜잭션 경계:** [process]는 @Transactional로 감싸지 않는다. [ArtifactGenerationService]가 외부 LLM 호출 전후로
 * 트랜잭션을 짧게 끊어 자체 관리하기 때문이다(긴 트랜잭션 금지). 결과 저장(markSucceeded/markFailed)은 각자
 * 짧은 영속으로 처리한다.
 */
@Component
class GenerationJobWorker(
    private val jobRepository: GenerationJobRepository,
    private val generationService: ArtifactGenerationService,
    private val properties: GenerationJobProperties,
    private val clock: Clock,
) {

    @Scheduled(fixedDelayString = "\${resumaker.generation-job.poll-interval-ms:2000}")
    fun poll() {
        recoverStale()
        claimAndProcessOne()
    }

    /**
     * 고아 RUNNING 회수: startedAt이 staleRunningTimeout보다 과거인 RUNNING 작업을 FAILED로 종료한다. 워커가 처리
     * 도중 죽으면 작업이 영원히 RUNNING으로 남으므로, 시간 초과를 죽음으로 간주해 정리한다(자동 재시도하지 않음).
     */
    fun recoverStale() {
        val now = Instant.now(clock)
        val cutoff = now.minus(properties.staleRunningTimeout)
        jobRepository.findByStatusAndStartedAtBefore(GenerationJobStatus.RUNNING, cutoff).forEach { job ->
            job.markFailed(
                code = GenerationErrorCode.AI_UNAVAILABLE,
                message = "생성이 예상보다 오래 걸려 중단됐어요. 다시 시도해 주세요.",
                now = now,
            )
            jobRepository.save(job)
        }
    }

    /**
     * 가장 오래된 PENDING 1건을 원자 클레임해 처리한다. claim이 1(이 호출이 소유)일 때만 reload 후 [process].
     * 0이면 다른 호출이 먼저 가져갔으니 이 틱은 건너뛴다.
     */
    fun claimAndProcessOne() {
        val pending = jobRepository.findFirstByStatusOrderByCreatedAtAsc(GenerationJobStatus.PENDING) ?: return
        val now = Instant.now(clock)
        if (jobRepository.claim(pending.id, now) != 1) {
            return
        }
        val claimed = jobRepository.findById(pending.id.value).orElse(null) ?: return
        process(claimed)
    }

    /**
     * 한 작업을 생성 파이프라인으로 처리한다. 성공이면 SUCCEEDED+artifactId, 예외면 코드별 FAILED로 저장한다.
     * 부분 성공(≥1 항목)은 정상 반환되어 SUCCEEDED가 된다(산출물 항목에 *_FAILED 보존). 가드레일 차감은
     * [ArtifactGenerationService] 내부에서 일어난다(워커가 따로 차감하지 않음).
     */
    fun process(job: GenerationJob) {
        try {
            // B3: 작업 완료(SUCCEEDED 저장)를 산출물 영속·차감과 같은 tx2에서 원자적으로 처리한다. tx2 커밋과
            // markSucceeded 저장이 분리돼 그 사이 크래시 시 "성공 산출물 + IN_PLACE 재시도 이중 차감"이 나던
            // 비원자 완료를 막는다. 훅이 실행되면 산출물·차감·작업완료가 함께 커밋되거나 함께 롤백된다.
            val onPersisted: (String) -> Unit = { artifactId ->
                job.markSucceeded(UUID.fromString(artifactId), Instant.now(clock))
                jobRepository.save(job)
            }
            when (job.kind) {
                watson.resumaker.artifact.domain.ArtifactKind.RESUME -> generationService.generateResume(
                    job.ownerId,
                    GenerateResumeCommand(
                        experienceIds = job.experienceIds.map { ExperienceRecordId(it) },
                        targetId = TargetBriefId(job.targetId),
                        templateId = job.templateId?.let { ResumeTemplateId(it) },
                    ),
                    onPersisted,
                )
                watson.resumaker.artifact.domain.ArtifactKind.PORTFOLIO -> generationService.generatePortfolio(
                    job.ownerId,
                    GeneratePortfolioCommand(
                        experienceIds = job.experienceIds.map { ExperienceRecordId(it) },
                        targetId = TargetBriefId(job.targetId),
                    ),
                    onPersisted,
                )
            }
        } catch (exception: QuotaExceededException) {
            // 가드레일 상한(제출 후 다른 생성으로 소진된 경우). 클라이언트 안내용 코드·메시지를 그대로 보존한다.
            fail(job, exception.code, exception.message ?: "오늘 만들 수 있는 횟수를 모두 썼어요.")
        } catch (exception: ClaudeCliException) {
            // 외부 AI 일시 불가(API 키 없음·CLI 비정상 종료·파싱 오류 등).
            fail(job, GenerationErrorCode.AI_UNAVAILABLE, "지금은 AI 생성을 사용할 수 없어요. 잠시 후 다시 시도해 주세요.")
        } catch (exception: GenerationUnavailableException) {
            // B5: 전 항목 일시 실패(LLM이 본문을 끝내 못 만듦) — 입력 문제가 아니므로 같은 입력으로 다시 만들 수 있게
            // AI_UNAVAILABLE(IN_PLACE)로 분류한다. 근거 0/구조 불일치(NO_CONTENT→EDIT_INPUTS)와 구분.
            fail(job, GenerationErrorCode.AI_UNAVAILABLE, exception.message ?: "지금은 생성 결과를 만들지 못했어요. 잠시 후 다시 시도해 주세요.")
        } catch (exception: DomainValidationException) {
            // 전 항목 실패 등으로 만들 산출물이 없는 경우(파이프라인이 "생성할 수 있는 항목이 없어요…"를 던짐).
            fail(job, GenerationErrorCode.NO_CONTENT, exception.message ?: "생성할 수 있는 항목이 없어요.")
        } catch (exception: ResourceNotFoundException) {
            // 제출 후 경험·목표가 삭제돼 생성 재료를 적재하지 못한 경우.
            fail(job, GenerationErrorCode.SOURCE_MISSING, "생성에 쓸 경험이나 목표를 찾을 수 없어요.")
        } catch (exception: Throwable) {
            // 그 외 예기치 못한 실패. 내부 정보는 노출하지 않고 일반 안내로 종료한다.
            fail(job, GenerationErrorCode.GENERATION_FAILED, "생성 중 문제가 생겼어요. 다시 시도해 주세요.")
        }
    }

    /** 작업을 실패로 종료해 영속한다. 완료 시각은 호출 시점 시계로 새로 찍는다(catch 경계를 넘는 지역 변수 의존 제거). */
    private fun fail(job: GenerationJob, code: String, message: String) {
        job.markFailed(code, message, Instant.now(clock))
        jobRepository.save(job)
    }
}
