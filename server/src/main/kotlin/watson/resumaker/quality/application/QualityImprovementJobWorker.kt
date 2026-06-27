package watson.resumaker.quality.application

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.GenerationQuotaGuard
import watson.resumaker.generation.application.TargetSnapshot
import watson.resumaker.generation.infrastructure.ClaudeCliException
import watson.resumaker.quality.domain.QualityCandidate
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.QualityImprovementJob
import watson.resumaker.quality.domain.QualityImprovementJobStatus
import watson.resumaker.quality.infrastructure.QualityCandidateRepository
import watson.resumaker.quality.infrastructure.QualityImprovementJobRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 품질 개선 작업 워커([watson.resumaker.generation.application.GenerationJobWorker] 동형). @Scheduled 폴링으로 대기열을 처리한다.
 *
 * **한 틱의 일(poll):**
 * 1. [recoverStale]: 너무 오래 RUNNING인 고아 작업을 FAILED로 회수한다(영원히 RUNNING 방지 — QC6). 재시도하지 않는다.
 * 2. [claimAndProcessOne]: 가장 오래된 PENDING 1건을 원자 클레임해 처리한다(claim이 1을 줄 때만 소유).
 *
 * **트랜잭션 경계(트랜잭션 분리 가이드 — LLM은 트랜잭션 밖):** [process]는 @Transactional로 감싸지 않는다.
 * 재료 적재(tx1) → **처치 생성·검증(tx 밖, 외부 LLM)** → 후보 영속·상태 확정(tx2)으로 끊어 관리한다
 * ([watson.resumaker.generation.application.ArtifactGenerationService]의 3단 경계 복제).
 *
 * **차감 시점:** 비용 가드레일 차감은 **채택**(CandidateAdoptionService) 시점이 아니라 작업 성공(후보 ≥1 영속)
 * 시점이다(오너 확정 §5.1-3 "채택 가능 후보 ≥1일 때 1회 차감"). 차감은 후속 커밋에서 배선한다(이 워커는 후보 영속까지).
 */
@Component
class QualityImprovementJobWorker(
    private val jobRepository: QualityImprovementJobRepository,
    private val candidateRepository: QualityCandidateRepository,
    private val artifactRepository: ArtifactRepository,
    private val experienceRepository: ExperienceRecordRepository,
    private val processor: QualityImprovementProcessor,
    private val checks: watson.resumaker.quality.infrastructure.QualityCriteriaDictionary,
    private val quotaGuard: GenerationQuotaGuard,
    private val properties: QualityImprovementJobProperties,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {

    @Scheduled(fixedDelayString = "\${resumaker.quality-improvement-job.poll-interval-ms:2000}")
    fun poll() {
        recoverStale()
        claimAndProcessOne()
    }

    /**
     * 고아 RUNNING 회수: startedAt이 staleRunningTimeout보다 과거인 RUNNING 작업을 FAILED로 종료한다(QC6 수렴).
     */
    fun recoverStale() {
        val now = Instant.now(clock)
        val cutoff = now.minus(properties.staleRunningTimeout)
        jobRepository.findByStatusAndStartedAtBefore(QualityImprovementJobStatus.RUNNING, cutoff).forEach { job ->
            job.markFailed(
                code = "QUALITY_IMPROVEMENT_UNAVAILABLE",
                message = "개선이 예상보다 오래 걸려 중단됐어요. 다시 시도해 주세요.",
                now = now,
            )
            jobRepository.save(job)
        }
    }

    /** 가장 오래된 PENDING 1건을 원자 클레임해 처리한다. claim이 1(이 호출이 소유)일 때만 reload 후 [process]. */
    fun claimAndProcessOne() {
        val pending = jobRepository.findFirstByStatusOrderByCreatedAtAsc(QualityImprovementJobStatus.PENDING) ?: return
        val now = Instant.now(clock)
        if (jobRepository.claim(pending.id, now) != 1) {
            return
        }
        val claimed = jobRepository.findById(pending.id.value).orElse(null) ?: return
        process(claimed)
    }

    /**
     * 한 작업을 처치 파이프라인으로 처리한다. 재료 적재(tx1) → 항목별 처치·검증(tx 밖) → 후보 영속·상태 확정(tx2).
     * 채택 가능한 후보가 ≥1이면 SUCCEEDED, 0건(전 항목 검증/생성 실패)이면 FAILED. 예외별 FAILED 매핑.
     */
    fun process(job: QualityImprovementJob) {
        val now = Instant.now(clock)
        try {
            // 1. (tx) 재료 적재 — 산출물·항목·출처 경험 스냅샷을 소유 격리로 적재해 처치 입력으로 좁힌다.
            val inputs = requireNotNull(
                transactionTemplate.execute { loadInputs(job) },
            ) { "품질 개선 재료 적재 트랜잭션이 결과를 돌려주지 못했어요." }

            if (inputs.isEmpty()) {
                // 접수 후 활성 버전이 바뀌어 소견 항목이 사라졌거나 출처 경험이 모두 삭제됨 → 만들 후보 없음.
                job.markFailed("QUALITY_IMPROVEMENT_NO_CONTENT", "다듬을 항목을 찾을 수 없어요.", now)
                jobRepository.save(job)
                return
            }

            // 2. (tx 밖) 항목별 처치 생성 + 검증(QC3·QC4) + 검증실패 자동 1회 재시도.
            val candidates = inputs.mapNotNull { input ->
                processor.process(input.toPortInput())?.let { generated ->
                    QualityCandidate.create(
                        jobId = job.id.value,
                        sectionId = input.sectionId,
                        definitionKey = input.definitionKey,
                        originalContent = input.originalContent,
                        candidateContent = generated.content,
                        appliedCriterionIds = input.criterionIds,
                    )
                }
            }

            // 3. (tx2) 후보 영속 + 차감 + 상태 확정을 **한 트랜잭션에서 원자 처리**한다(생성 워커 B3와 대칭).
            //    채택 가능 후보 ≥1이면 성공, 0건(전 항목 검증/생성 실패)이면 실패(QC7).
            transactionTemplate.execute {
                if (candidates.isEmpty()) {
                    // 전 항목 검증/생성 실패 → 미차감(QC7), 원본 유지.
                    job.markFailed(
                        "QUALITY_IMPROVEMENT_NO_CANDIDATE",
                        "안전하게 다듬을 수 있는 항목이 없어 원본을 유지했어요.",
                        now,
                    )
                } else {
                    // B2: 처리 시점 재점검(1차 생성 워커의 tx 점검과 대칭). 점검은 접수 시점, 차감은 처리 시점이라
                    // 시차에 여러 건을 빠르게 접수하면 모두 0회에서 통과해 상한을 초과 차감하던 우회를 막는다.
                    // 한도가 이미 소진됐으면 후보 영속·차감 없이 QUOTA 초과로 종료한다.
                    val quotaExceededCode = try {
                        quotaGuard.checkQualityImprovement(job.ownerId)
                        null
                    } catch (exceeded: QuotaExceededException) {
                        exceeded.code
                    }
                    if (quotaExceededCode != null) {
                        job.markFailed(
                            quotaExceededCode,
                            "오늘 품질 개선 횟수를 모두 써서 이 작업을 진행하지 못했어요. 내일 다시 시도하거나 항목을 직접 편집해 보세요.",
                            now,
                        )
                    } else {
                        // B8(개정): 후보 영속·차감(QC7)·작업완료를 **한 tx2에 둬 원자 완료**한다. 차감을 별도 보상 tx로
                        // 분리하면 "커밋과 차감 사이 크래시 시 미차감 성공"이라는 비원자 완료(생성에서 B3가 닫은 것)를
                        // 품질에 다시 여는 셈이라 한 tx로 되돌린다. 차감의 유일한 선행 실패원인(loadUser 부재)은 바로 위
                        // checkQualityImprovement가 같은 tx에서 이미 통과시키므로 record엔 카운터 증가만 남는다. 그 증가가
                        // 실패하면 전체 롤백(처치 유실 → FAILED 후 재시도)이 미차감 성공보다 비용 가드레일에 안전하다.
                        // record→markSucceeded 순서라 차감 실패 시 SUCCEEDED로 굳지 않는다(예외가 catch로 가 FAILED 처리).
                        candidateRepository.saveAll(candidates)
                        quotaGuard.recordQualityImprovement(job.ownerId)
                        job.markSucceeded(now)
                    }
                }
                jobRepository.save(job)
            }
        } catch (exception: ClaudeCliException) {
            job.markFailed("QUALITY_IMPROVEMENT_UNAVAILABLE", "지금은 AI 개선을 사용할 수 없어요. 잠시 후 다시 시도해 주세요.", now)
            jobRepository.save(job)
        } catch (exception: Throwable) {
            job.markFailed("QUALITY_IMPROVEMENT_FAILED", "개선 중 문제가 생겼어요. 다시 시도해 주세요.", now)
            jobRepository.save(job)
        }
    }

    // ----- 재료 적재(tx 안) -----

    /**
     * 작업의 findingIds(`{sectionId}:{criterionId}`)를 현재 활성 버전의 항목·기준으로 복원해 처치 입력을 만든다.
     * 항목별로 적용 기준을 모은다(한 항목에 여러 기준이 걸릴 수 있음 — 한 번의 처치로 함께 다듬는다).
     * 활성 버전이 접수 시점과 다르거나(versionId 불일치) 항목이 사라졌으면 그 입력은 빠진다(빈 목록이면 FAILED).
     */
    private fun loadInputs(job: QualityImprovementJob): List<ImprovementMaterial> {
        val artifact = artifactRepository.findByIdAndOwnerId(
            watson.resumaker.artifact.domain.ArtifactId(job.artifactId),
            job.ownerId,
        ) ?: return emptyList()
        val active = artifact.activeVersion()
        // 접수 시점 버전과 활성 버전이 다르면 후보가 현재 본문과 어긋난다(채택도 거부될 것) → 처치하지 않는다.
        if (active.id.value != job.versionId) return emptyList()

        // findingId 파싱: sectionId별로 적용 기준을 모은다.
        val criteriaBySection = LinkedHashMap<SectionId, MutableList<QualityCriterion>>()
        job.findingIds.forEach { findingId ->
            val parsed = parseFinding(findingId) ?: return@forEach
            criteriaBySection.getOrPut(parsed.first) { mutableListOf() }.add(parsed.second)
        }

        val experiencesById = loadExperiences(job.ownerId, active.sections)
        return criteriaBySection.mapNotNull { (sectionId, criteria) ->
            val section = active.sectionById(sectionId) ?: return@mapNotNull null
            val experiences = section.sourceExperienceIds.mapNotNull { experiencesById[it] }
            ImprovementMaterial(
                sectionId = section.id,
                definitionKey = section.definitionKey,
                sectionKind = section.sectionKind,
                originalContent = section.content.value,
                criteria = criteria,
                // AI-04: 라벨에 구체 위반 토큰(evidenceText)을 결정적으로 재산출해 정박한다("이 표현('담당했다')을"식).
                criteriaPrompts = criteria.map { criterionPrompt(it, section.content.value) },
                sourceExperienceIds = section.sourceExperienceIds,
                experiences = experiences.map { it.toSnapshot() },
                target = artifact.targetSnapshot.toGenerationTarget(),
                // AI-03: 중복(C3) 처치면 겹치는 짝 항목 본문을 함께 싣는다(처치가 겹침을 보고 덜어낼 수 있게).
                duplicatedWith = duplicatedBodyFor(section, criteria, active.sections),
            )
        }
    }

    /**
     * AI-04: 처치 라벨에 구체 위반 토큰을 정박한다. findingId가 evidenceText를 싣지 않으므로 진단과 같은 검사기로
     * 결정적으로 다시 찾아(스키마 무변경) "이 표현('담당했다')을 그 부분만 고치라"고 지시해 과도한 재작성을 줄인다.
     * 토큰 evidence가 없는 기준(길이·중복 등)은 라벨만 둔다.
     */
    private fun criterionPrompt(criterion: QualityCriterion, content: String): String {
        val evidence = when (criterion) {
            QualityCriterion.STRONG_VERB -> checks.findWeakVerb(content)
            QualityCriterion.ACTIVE_VOICE -> checks.findPassiveVoice(content)
            QualityCriterion.BUZZWORD -> checks.findBuzzword(content)
            QualityCriterion.VAGUE_METRIC -> checks.findVagueMetric(content)
            else -> null
        }
        return if (evidence != null) {
            "${criterion.label} (특히 ‘$evidence’ 표현을 정박해 그 부분만 고치고, 나머지는 가능한 한 그대로 두세요)"
        } else {
            criterion.label
        }
    }

    /**
     * AI-03: 항목 기준에 중복(C3)이 있으면 활성 버전에서 이 항목과 겹치는 **다른 항목의 본문**을 찾아 돌려준다
     * (없으면 null). 진단의 중복 판정(checks.isDuplicate)을 그대로 재사용해 짝을 재식별한다(findingId가 짝 키를
     * 싣지 않으므로 워커가 결정적으로 다시 찾는다 — 스키마 무변경).
     */
    private fun duplicatedBodyFor(
        section: ArtifactSection,
        criteria: List<QualityCriterion>,
        allSections: List<ArtifactSection>,
    ): String? {
        if (!criteria.contains(QualityCriterion.DUPLICATION)) return null
        return allSections.firstOrNull { other ->
            other.id != section.id && other.content.value.isNotBlank() &&
                checks.isDuplicate(section.content.value, other.content.value)
        }?.content?.value
    }

    private fun parseFinding(findingId: String): Pair<SectionId, QualityCriterion>? {
        val sep = findingId.lastIndexOf(':')
        if (sep <= 0) return null
        val sectionPart = findingId.substring(0, sep)
        val criterionPart = findingId.substring(sep + 1)
        val sectionId = runCatching { SectionId(UUID.fromString(sectionPart)) }.getOrNull() ?: return null
        val criterion = QualityCriterion.entries.firstOrNull { it.criterionId == criterionPart } ?: return null
        return sectionId to criterion
    }

    private fun loadExperiences(
        ownerId: UserId,
        sections: List<ArtifactSection>,
    ): Map<ExperienceRecordId, ExperienceRecord> {
        val ids = sections.flatMap { it.sourceExperienceIds }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return experienceRepository.findAllByIdInAndOwnerId(ids.map { it.value }, ownerId)
            .associateBy { it.id }
    }

    private fun ExperienceRecord.toSnapshot(): ExperienceSnapshot = ExperienceSnapshot(
        id = id,
        title = title.value,
        body = body.value,
        situation = detail.situation,
        action = detail.action,
        result = detail.result,
        skillTags = detail.skillTags.map { it.value },
    )

    private fun ArtifactTargetSnapshot.toGenerationTarget(): TargetSnapshot = TargetSnapshot(
        recruitDirection = recruitDirection,
        company = company,
        job = job,
    )

    /** tx1에서 적재한 한 항목 처치 재료(스냅샷). 지연 로딩 경계를 넘지 않도록 엔티티가 아닌 스냅샷만 담는다. */
    private data class ImprovementMaterial(
        val sectionId: SectionId,
        val definitionKey: String,
        val sectionKind: watson.resumaker.artifact.domain.SectionKind,
        val originalContent: String,
        val criteria: List<QualityCriterion>,
        val criteriaPrompts: List<String>,
        val sourceExperienceIds: List<ExperienceRecordId>,
        val experiences: List<ExperienceSnapshot>,
        val target: TargetSnapshot,
        val duplicatedWith: String? = null,
    ) {
        val criterionIds: List<String> get() = criteria.map { it.criterionId }

        fun toPortInput(): QualityImprovementInput = QualityImprovementInput(
            definitionKey = definitionKey,
            sectionKind = sectionKind,
            originalContent = originalContent,
            criteria = criteriaPrompts,
            target = target,
            experiences = experiences,
            sourceExperienceIds = sourceExperienceIds,
            duplicatedWith = duplicatedWith,
        )
    }
}
