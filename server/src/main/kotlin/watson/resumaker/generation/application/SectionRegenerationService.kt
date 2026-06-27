package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.infrastructure.ArtifactVersioningProperties
import watson.resumaker.generation.presentation.ArtifactResponse
import java.time.Clock
import java.time.Instant

/**
 * 항목 단위 재생성 유스케이스(도메인 이해 §5 개선/재생성·§256~284, 구현 설계 §11 태스크 5, 수용 기준 10·19·20).
 *
 * 활성 버전의 한 생성 항목만 AI로 다시 만들어, 직전 활성 버전을 복제한 위에 그 항목만 교체한 **새 버전**을 만들고
 * 활성으로 전환한다(도메인 [Artifact.adoptSection] 재사용 — 다른 항목은 그대로 복제, 이전 버전 보존).
 *
 * **트랜잭션 경계(구현 설계 §5, 1차 생성과 동형):**
 * 1. (tx) 적재·검증 — 산출물 소유 격리 적재(404), 활성 버전에서 대상 항목 적재(404), 출처 경험·목표 적재, 좁힌 재료 구성.
 * 2. **(tx 밖)** 포트 생성 + 자동 검증 + 검증실패 자동 1회 재생성([SectionRegenerationProcessor] 공유 회복).
 * 3. (tx) 재적재 후 [Artifact.adoptSection]으로 새 버전·활성 전환, 영속, Response DTO 변환(지연 로딩 경계 내부).
 *
 * **동시 재생성 거절(수용 기준 20):** [SectionRegenerationLocks]로 같은 항목 중복 진행을 거절한다(409). 서로 다른
 * 항목은 병렬 허용. 점유는 임계 구간(적재~영속) 전체를 감싸고 finally에서 해제한다.
 *
 * **재생성 한도(§397):** 사용자 요청 재생성은 외부 호출 전 [GenerationQuotaGuard.checkRegeneration]으로 항목당
 * 잔여 횟수를 점검(상한 도달 시 429로 차단)하고, tx2 영속 후 최종 성공(GENERATED) 시에만
 * [GenerationQuotaGuard.recordRegeneration]으로 생성 항목당 1회 차감한다. 검증실패 자동 재시도는 차감 대상이
 * 아니다(프로세서가 가드를 호출하지 않음 — 구조적 미차감).
 */
@Service
class SectionRegenerationService(
    private val artifactRepository: ArtifactRepository,
    private val experienceRepository: ExperienceRecordRepository,
    private val generationPort: ArtifactGenerationPort,
    private val processor: SectionRegenerationProcessor,
    private val quotaGuard: GenerationQuotaGuard,
    private val locks: SectionRegenerationLocks,
    private val mapper: ArtifactReadServiceMapper,
    private val versioningProperties: ArtifactVersioningProperties,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {

    fun regenerateSection(ownerId: UserId, command: RegenerateSectionCommand): ArtifactResponse {
        // 수용 기준 20: 같은 항목 동시 재생성 거절. 점유 실패면 진행 중을 알린다(409 + 안내).
        if (!locks.tryAcquire(command.sectionId)) {
            throw ConflictException(
                "이 항목은 지금 다시 만드는 중이에요. 잠시 후 결과를 확인하거나 다시 시도해 주세요.",
                action = "RETRY_LATER",
            )
        }
        try {
            // 1. (tx) 적재·검증 + 좁힌 재료 구성.
            val prepared = requireNotNull(
                transactionTemplate.execute { loadRegenerationMaterial(ownerId, command) },
            ) { "항목 재생성 재료 적재 트랜잭션이 결과를 돌려주지 못했어요." }

            // 재생성 사전 점검(빠른 실패): 외부 LLM 호출 전에 항목당 잔여 횟수를 확인해 상한 도달 시 즉시 막는다(§397).
            // 쿼터 키는 버전 불변 논리 항목(artifactId+definitionKey)이다 — 재생성이 새 SectionId를 발급해도 누적된다(B1).
            quotaGuard.checkRegeneration(ownerId, command.artifactId, prepared.definitionKey)

            // 2. (tx 밖) 포트 생성 + 자동 검증 + 검증실패 자동 1회 재생성(공유 회복 규칙).
            //    포트가 대상 키 항목을 끝내 누락하면 resolved는 null(→ 영속 단계에서 거부).
            val generated = generationPort.generate(prepared.material).sections
                .firstOrNull { it.definitionKey == prepared.definitionKey }
            val resolved = generated?.let { processor.resolve(prepared.material, it) }

            // 3. (tx) 재적재 → adoptSection으로 새 버전·활성 전환 → 영속 → 응답 변환(지연 로딩 경계 내부).
            return requireNotNull(
                transactionTemplate.execute { adoptAndMap(ownerId, command, resolved) },
            ) { "항목 재생성 영속 트랜잭션이 응답을 돌려주지 못했어요." }
        } finally {
            locks.release(command.sectionId)
        }
    }

    // ----- 1. 적재·검증(tx 안) -----

    private fun loadRegenerationMaterial(ownerId: UserId, command: RegenerateSectionCommand): PreparedRegeneration {
        val artifact = artifactRepository.findByIdAndOwnerId(command.artifactId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")
        val section = artifact.activeVersion().sectionById(command.sectionId)
            ?: throw ResourceNotFoundException("다시 만들 항목을 찾을 수 없어요.")

        // 목표는 산출물의 불변 스냅샷에서 읽는다(§347·§364: 목표 변경 = 새 산출물, 재생성은 원본 목표로).
        val targetSnapshot = artifact.targetSnapshot
        // 항목 출처 경험을 소유 격리로 적재한다(삭제된 경험은 빠질 수 있음 — 스냅샷 격리, 구현 설계 §164).
        val experiences = loadExperiences(ownerId, section.sourceExperienceIds)

        val material = when (artifact.kind) {
            ArtifactKind.RESUME -> buildResumeMaterial(artifact, section, targetSnapshot, experiences, command.directive)
            ArtifactKind.PORTFOLIO -> buildPortfolioMaterial(section, targetSnapshot, experiences, command.directive)
        }
        return PreparedRegeneration(definitionKey = section.definitionKey, material = material)
    }

    private fun buildResumeMaterial(
        artifact: Artifact,
        section: ArtifactSection,
        targetSnapshot: ArtifactTargetSnapshot,
        experiences: List<ExperienceRecord>,
        directive: String?,
    ): GenerationMaterial {
        // 양식 스냅샷에서 이 항목의 섹션 정의를 키로 찾아 단일 섹션 재료로 좁힌다(구조 불변, 내용만 갱신 — §364).
        val snapshot = artifact.templateSnapshot
            ?: throw DomainValidationException("이력서 산출물에 양식 스냅샷이 없어요.")
        val definition = snapshot.sectionByKey(section.definitionKey)
            ?: throw ResourceNotFoundException("다시 만들 항목의 양식 정의를 찾을 수 없어요.")
        return GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = experiences.map { it.toSnapshot() },
            target = targetSnapshot.toGenerationTarget(),
            templateSections = listOf(
                TemplateSectionSpec(
                    definitionKey = definition.definitionKey,
                    name = definition.name,
                    sectionKind = definition.sectionKind,
                    required = definition.required,
                ),
            ),
            selectedExperienceIds = emptyList(),
            directive = directive,
        )
    }

    private fun buildPortfolioMaterial(
        section: ArtifactSection,
        targetSnapshot: ArtifactTargetSnapshot,
        experiences: List<ExperienceRecord>,
        directive: String?,
    ): GenerationMaterial = GenerationMaterial(
        kind = GenerationKind.PORTFOLIO,
        experiences = experiences.map { it.toSnapshot() },
        target = targetSnapshot.toGenerationTarget(),
        templateSections = emptyList(),
        // 포트폴리오 항목 키는 경험Id다. 그 경험만 산출하도록 선택 경험을 그 항목으로 한정한다(§357 1:1).
        selectedExperienceIds = section.sourceExperienceIds,
        directive = directive,
    )

    private fun loadExperiences(ownerId: UserId, ids: List<ExperienceRecordId>): List<ExperienceRecord> {
        if (ids.isEmpty()) {
            // 출처 경험이 없는 항목은 재생성할 근거가 없다(날조 금지 — §284). 사용자 안내 경로로 거부한다.
            throw DomainValidationException("이 항목의 근거 경험이 더 이상 없어 다시 만들 수 없어요. 관련 경험을 추가해 주세요.")
        }
        val distinctIds = ids.distinct()
        val byId = experienceRepository.findAllByIdInAndOwnerId(distinctIds.map { it.value }, ownerId)
            .associateBy { it.id }
        // 일부 출처 경험이 삭제됐어도 남은 근거로 재생성한다(스냅샷 격리). 전부 사라졌으면 아래에서 거부.
        val resolved = ids.mapNotNull { byId[it] }
        if (resolved.isEmpty()) {
            throw DomainValidationException("이 항목의 근거 경험이 더 이상 없어 다시 만들 수 없어요. 관련 경험을 추가해 주세요.")
        }
        return resolved
    }

    // ----- 3. 채택·영속(tx 안) -----

    private fun adoptAndMap(
        ownerId: UserId,
        command: RegenerateSectionCommand,
        resolved: ResolvedSection?,
    ): ArtifactResponse {
        val artifact = artifactRepository.findByIdAndOwnerId(command.artifactId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")
        if (resolved == null) {
            // 포트가 그 키 항목을 끝내 못 돌려줬다(생성 호출 자체가 항목을 누락). 새 버전 없이 거부한다.
            throw DomainValidationException("항목을 다시 만들지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
        val adopted = SectionContent.of(resolved.section.content)
        val now = Instant.now(clock)
        // 도메인 재사용: 직전 활성 버전 복제 + 이 항목만 교체 + 새 활성 버전(수용 기준 10·19).
        // tx1→tx2 사이에 다른 경로(채택·버전 정리 등)가 활성 버전을 바꿔 sectionId가 사라지면
        // adoptSection이 DomainValidationException을 던진다 → 400(이번 사이클 동시성 범위 밖 케이스).
        val newVersion = artifact.adoptSection(command.sectionId, adopted, now)
        // adoptSection은 교체 항목을 GENERATED로 둔다. 검증실패(VALIDATION_FAILED)면 그 상태를 반영해 보존한다(§429).
        if (resolved.status != SectionStatus.GENERATED) {
            newVersion.sectionByDefinitionKey(resolved.section.definitionKey)?.status = resolved.status
        }
        // 보관 상한 정리(수용 기준 11): 새 버전 추가로 상한을 넘으면 같은 tx2(영속) 안에서 가장 오래된 비활성
        // 버전부터 정리한다(orphanRemoval로 삭제 영속). 방금 만든 활성 버전은 제외되어 불변식 유지(§135).
        val pruned = artifact.pruneOldestIfExceeds(versioningProperties.versionRetentionLimit)
        val saved = artifactRepository.save(artifact)

        // 재생성 차감(§397): 사용자 요청 재생성이 최종 성공(GENERATED)했을 때만 생성 항목당 1회 차감한다.
        // 검증실패(VALIDATION_FAILED 등)는 미차감하며, 검증실패 자동 재시도는 프로세서가 가드를 호출하지 않아
        // 구조적으로 미차감이다. 영속 성공 후이므로 차감과 새 버전이 같은 tx2에서 원자적으로 함께 반영된다.
        if (resolved.status == SectionStatus.GENERATED) {
            // 버전 불변 논리 항목 키로 차감해 새 SectionId 발급에도 일일 한도가 누적된다(B1).
            quotaGuard.recordRegeneration(ownerId, command.artifactId, resolved.section.definitionKey)
        }
        return mapper.toResponse(saved, prunedVersionCount = pruned.size)
    }

    // ----- 스냅샷 변환 -----

    private fun ExperienceRecord.toSnapshot(): ExperienceSnapshot = ExperienceSnapshot(
        id = id,
        title = title.value,
        body = body.value,
        situation = detail.situation,
        action = detail.action,
        result = detail.result,
        skillTags = detail.skillTags.map { it.value },
    )

    /** 산출물 목표 스냅샷 → 포트 재료용 [TargetSnapshot](생성 어댑터 계약). */
    private fun ArtifactTargetSnapshot.toGenerationTarget(): TargetSnapshot = TargetSnapshot(
        recruitDirection = recruitDirection,
        company = company,
        job = job,
    )

    private data class PreparedRegeneration(
        val definitionKey: String,
        val material: GenerationMaterial,
    )
}
