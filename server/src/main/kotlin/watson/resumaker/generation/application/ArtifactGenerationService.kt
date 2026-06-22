package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.FactGrounding
import watson.resumaker.artifact.domain.FactToken
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.EmptyExperienceSelectionException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.presentation.GenerationResponse
import watson.resumaker.generation.presentation.TemplateOrigin
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.infrastructure.TargetBriefRepository
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.infrastructure.ResumeTemplateRepository
import java.time.Clock
import java.time.Instant

/**
 * 이력서·포트폴리오 1차 생성 유스케이스(구현 설계 §5 트랜잭션 흐름 1~5).
 *
 * **트랜잭션 경계(설계 §5·트랜잭션 분리 가이드):**
 * 1. (tx) 재료 적재·검증 — 경험 묶음(ownerId 격리) 적재, **빈 묶음 거부**(수용 기준 8), 목표 채용 방향 필수,
 *    이력서는 양식 스냅샷 복제. 비용 가드레일 사전 점검([GenerationQuotaGuard.checkInitialGeneration], 차감은 tx4).
 * 2. **(tx 밖)** [ArtifactGenerationPort.generate] 호출 — 외부 LLM. 항목 단위 성공/실패 수집(부분 실패 허용 — 수용 기준 9).
 * 3. **(tx 밖)** 자동 검증([GroundingValidator], Cycle C seam — Cycle B는 permissive 통과).
 * 4. (tx) 영속화 — 성공·실패 항목을 함께 담은 초기 Version 저장·활성, Artifact 저장. **Response DTO 변환을
 *    이 트랜잭션 내부에서** 수행(지연 로딩 경계, 설계 §221).
 *
 * 외부 호출은 [TransactionTemplate]로 tx를 짧게 끊어 그 사이(2·3)에서 수행한다. @Transactional 메서드 하나로
 * 감싸면 외부 호출이 tx 안에 들어가므로 쓰지 않는다.
 *
 * **소유 격리:** 모든 적재는 ownerId 조건 + 결과 소유자 재검증(구현 설계 §194).
 */
@Service
class ArtifactGenerationService(
    private val experienceRepository: ExperienceRecordRepository,
    private val targetRepository: TargetBriefRepository,
    private val templateRepository: ResumeTemplateRepository,
    private val artifactRepository: ArtifactRepository,
    private val generationPort: ArtifactGenerationPort,
    private val templateGenerator: ResumeTemplateGenerator,
    private val quotaGuard: GenerationQuotaGuard,
    private val sectionRegenerationProcessor: SectionRegenerationProcessor,
    private val mapper: ArtifactGenerationServiceMapper,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
    private val clock: Clock,
) {

    fun generateResume(ownerId: UserId, command: GenerateResumeCommand): GenerationResponse {
        // 1. (tx) 재료 적재·검증 + 가드레일 점검
        // 양식 지정 시 양식 스냅샷을 tx1에서 적재한다. 미지정(AI 생성 양식)이면 섹션은 tx 밖에서 채운다.
        val loaded = requireNotNull(
            transactionTemplate.execute {
                // Cycle 6: 가드레일 차감은 tx2(영속 후, 최소 1항목 성공 시)에서 수행 — tx1은 사전 점검만.
                quotaGuard.checkInitialGeneration(ownerId)
                val experiences = loadExperiences(ownerId, command.experienceIds)
                val target = loadTarget(ownerId, command.targetId)
                val templateSections = command.templateId?.let { loadTemplateSections(ownerId, it) }
                LoadedResumeMaterial(
                    experiences = experiences.map { it.toSnapshot() },
                    target = target.toSnapshot(),
                    targetSnapshot = target.toArtifactTargetSnapshot(),
                    templateSections = templateSections,
                )
            },
        ) { "이력서 재료 적재 트랜잭션이 결과를 돌려주지 못했어요." }

        // 1.5. (tx 밖) 양식 미지정이면 AI 생성 양식 포트로 섹션 구조를 만든다(외부 LLM — tx 밖, §178).
        //   생성 실패/비가용이면 막다른 길 금지: 기본 구조(프리셋류)로 폴백해 진행한다(§186).
        val (templateSections, templateOrigin) = when {
            loaded.templateSections != null ->
                loaded.templateSections to TemplateOrigin.USER_SELECTED
            else ->
                generateTemplateSectionsWithOrigin(loaded.experiences, loaded.target)
        }
        val material = buildResumeMaterial(loaded.experiences, loaded.target, templateSections)
        val prepared = PreparedGeneration(material = material, targetSnapshot = loaded.targetSnapshot)

        // 2·3. (tx 밖) 외부 생성 + 결정적 자동 검증 + 검증실패 자동 1회 재생성(Cycle C)
        val output = generationPort.generate(prepared.material)
        val resolved = validateAndAutoRegenerate(prepared.material, output.sections)

        // 4. (tx) 영속화 + Response DTO 변환(트랜잭션 내부)
        // 가드레일 차감은 tx2(영속 후, 최소 1항목 성공 시)에서 수행한다(tx1에 넣지 말 것).
        return requireNotNull(
            transactionTemplate.execute {
                val templateSnapshot = toTemplateSnapshot(prepared.material.templateSections)
                val response = persistAndMap(ownerId, ArtifactKind.RESUME, prepared.targetSnapshot, templateSnapshot, resolved, prepared.material, templateOrigin)
                // persistAndMap이 빈 결과면 throw하므로 여기 도달 = 최소 1항목 영속 성공 → 1차 생성 1회 차감(§396).
                quotaGuard.recordInitialGeneration(ownerId)
                response
            },
        ) { "이력서 영속 트랜잭션이 응답을 돌려주지 못했어요." }
    }

    fun generatePortfolio(ownerId: UserId, command: GeneratePortfolioCommand): GenerationResponse {
        // 1. (tx) 재료 적재·검증 + 가드레일 점검
        val prepared = requireNotNull(
            transactionTemplate.execute {
                // Cycle 6: 가드레일 차감은 tx2(영속 후, 최소 1항목 성공 시)에서 수행 — tx1은 사전 점검만.
                quotaGuard.checkInitialGeneration(ownerId)
                val experiences = loadExperiences(ownerId, command.experienceIds)
                val target = loadTarget(ownerId, command.targetId)
                PreparedGeneration(
                    material = buildPortfolioMaterial(experiences, target),
                    targetSnapshot = target.toArtifactTargetSnapshot(),
                )
            },
        ) { "포트폴리오 재료 적재 트랜잭션이 결과를 돌려주지 못했어요." }

        // 2·3. (tx 밖) 외부 생성 + 결정적 자동 검증 + 검증실패 자동 1회 재생성(Cycle C)
        val output = generationPort.generate(prepared.material)
        val resolved = validateAndAutoRegenerate(prepared.material, output.sections)

        // 4. (tx) 영속화 + Response DTO 변환(트랜잭션 내부)
        // 가드레일 차감은 tx2(영속 후, 최소 1항목 성공 시)에서 수행한다(tx1에 넣지 말 것).
        return requireNotNull(
            transactionTemplate.execute {
                val response = persistAndMap(ownerId, ArtifactKind.PORTFOLIO, prepared.targetSnapshot, templateSnapshot = null, resolved = resolved, material = prepared.material, templateOrigin = TemplateOrigin.NONE)
                // persistAndMap이 빈 결과면 throw하므로 여기 도달 = 최소 1항목 영속 성공 → 1차 생성 1회 차감(§396).
                quotaGuard.recordInitialGeneration(ownerId)
                response
            },
        ) { "포트폴리오 영속 트랜잭션이 응답을 돌려주지 못했어요." }
    }

    // ----- 자동 검증 + 검증실패 자동 1회 재생성(tx 밖, Cycle C, 도메인 이해 §421~429·§3.6) -----

    /**
     * 포트 생성 결과 항목들을 [SectionRegenerationProcessor]에 위임해 검증·자동 1회 재생성한다(공유 회복 규칙 —
     * 항목 단위 재생성과 진실의 원천 하나). 트랜잭션 밖에서 수행한다(포트 재호출이 외부 LLM이므로).
     */
    private fun validateAndAutoRegenerate(
        material: GenerationMaterial,
        sections: List<GeneratedSection>,
    ): List<ResolvedSection> = sections.map { sectionRegenerationProcessor.resolve(material, it) }

    // ----- 재료 적재(tx 안) -----

    private fun loadExperiences(ownerId: UserId, ids: List<ExperienceRecordId>): List<ExperienceRecord> {
        if (ids.isEmpty()) {
            // 빈 경험 묶음 거부(수용 기준 8). 형식 오류(400)가 아니라 생성 불가 충돌(409)로 매핑해
            // 경험 추가 유도 action을 함께 내린다(구현 설계 §9, 전역 핸들러).
            throw EmptyExperienceSelectionException("이력서·포트폴리오를 만들려면 경험을 하나 이상 골라 주세요.")
        }
        // N+1 회피: ownerId 조건 배치 쿼리 1회로 적재한다(소유 격리 유지).
        val distinctIds = ids.distinct()
        val byId = experienceRepository.findAllByIdInAndOwnerId(distinctIds.map { it.value }, ownerId)
            .associateBy { it.id }
        // 미존재/타소유 식별자 검출: 요청한 고유 식별자가 모두 조회돼야 한다.
        if (byId.size != distinctIds.size) {
            throw ResourceNotFoundException("선택한 경험 중 일부를 찾을 수 없어요.")
        }
        // 요청한 순서를 보존해 돌려준다(배치 쿼리는 순서를 보장하지 않는다).
        return ids.map { id ->
            byId[id] ?: throw ResourceNotFoundException("선택한 경험 중 일부를 찾을 수 없어요.")
        }
    }

    private fun loadTarget(ownerId: UserId, targetId: watson.resumaker.target.domain.TargetBriefId): TargetBrief =
        targetRepository.findByIdAndOwnerId(targetId, ownerId)
            ?: throw ResourceNotFoundException("선택한 목표 정보를 찾을 수 없어요.")

    private fun loadTemplateSections(
        ownerId: UserId,
        templateId: watson.resumaker.template.domain.ResumeTemplateId,
    ): List<TemplateSectionSpec> {
        // 지정 양식 경로: 소유 격리로 양식을 적재해 섹션 사양으로 변환한다(미지정은 AI 생성 양식 경로).
        val template = templateRepository.findByIdAndOwnerId(templateId, ownerId)
            ?: throw ResourceNotFoundException("선택한 이력서 양식을 찾을 수 없어요.")
        return template.sections.toSectionSpecs()
    }

    /**
     * AI 생성 양식 경로(도메인 이해 §178·§446, 수용 기준 22): 양식 미지정 시 경험·목표로 섹션 구조를 만든다.
     * 외부 LLM 호출이므로 tx 밖에서 수행한다([generationPort] 호출과 같은 tx 경계 규율).
     *
     * **폴백(§186 "막다른 길 금지", 트레이드오프 — 진행 우선):** 생성기가 [ResumeTemplateGeneration.Unavailable]을
     * 돌려주면(LLM 미연결·실패·파싱 불가) 차단하지 않고 기본 구조([DEFAULT_TEMPLATE_SECTIONS], 프리셋류)로
     * 진행한다. 무관한 목표·붙여넣기 폴백 정책과 일관되게, "사용 불가로 생성 자체를 막기"보다 "기본 구조로
     * 초안을 주는" 쪽을 택한다(사용자는 생성 후 항목 편집·재생성으로 다듬을 수 있다).
     *
     * 출처 신호([TemplateOrigin])를 함께 돌려줘 응답 DTO에 폴백 여부를 실어 보낸다(§187 고지 원칙).
     */
    private fun generateTemplateSectionsWithOrigin(
        experiences: List<ExperienceSnapshot>,
        target: TargetSnapshot,
    ): Pair<List<TemplateSectionSpec>, TemplateOrigin> {
        return when (val result = templateGenerator.generate(
            ResumeTemplateGenerationInput(experiences = experiences, target = target),
        )) {
            is ResumeTemplateGeneration.Generated ->
                result.sections.toSectionSpecs() to TemplateOrigin.AI_GENERATED
            ResumeTemplateGeneration.Unavailable ->
                DEFAULT_TEMPLATE_SECTIONS.toSectionSpecs() to TemplateOrigin.AI_FALLBACK_DEFAULT
        }
    }

    private fun List<SectionDefinition>.toSectionSpecs(): List<TemplateSectionSpec> =
        mapIndexed { index, definition ->
            TemplateSectionSpec(
                definitionKey = definitionKeyOf(index, definition),
                name = definition.name,
                sectionKind = definition.character.toSectionKind(),
                required = definition.required,
            )
        }

    private fun buildResumeMaterial(
        experiences: List<ExperienceSnapshot>,
        target: TargetSnapshot,
        templateSections: List<TemplateSectionSpec>,
    ): GenerationMaterial = GenerationMaterial(
        kind = GenerationKind.RESUME,
        experiences = experiences,
        target = target,
        templateSections = templateSections,
        selectedExperienceIds = emptyList(),
    )

    private fun buildPortfolioMaterial(
        experiences: List<ExperienceRecord>,
        target: TargetBrief,
    ): GenerationMaterial = GenerationMaterial(
        kind = GenerationKind.PORTFOLIO,
        experiences = experiences.map { it.toSnapshot() },
        target = target.toSnapshot(),
        templateSections = emptyList(),
        selectedExperienceIds = experiences.map { it.id },
    )

    // ----- 영속화(tx 안) -----

    private fun persistAndMap(
        ownerId: UserId,
        kind: ArtifactKind,
        targetSnapshot: ArtifactTargetSnapshot,
        templateSnapshot: TemplateSnapshot?,
        resolved: List<ResolvedSection>,
        material: GenerationMaterial,
        templateOrigin: TemplateOrigin = TemplateOrigin.NONE,
    ): GenerationResponse {
        // 결정적 정합화(Cycle B 책임): 양식/구조 불변식에 어긋나는 고아·중복·종류 불일치 항목을 사전 드롭해
        // 도메인 init의 전체 hard-throw로 유료 생성이 통째 손실되지 않게 한다(§357·§371, 수용 기준 21/22).
        val reconciled = reconcileSections(kind, material, resolved)
        val sections = reconciled.map { it.toArtifactSection() }
        if (sections.isEmpty()) {
            // 실체화 항목 0(전 섹션 근거 0 등) — 도메인 불변식상 빈 버전은 성립하지 않으므로 거부한다.
            // (수용 기준 23: 근거 0 섹션은 미실체화하되, 전부 미실체화면 만들 산출물이 없다 — 사용자 안내 경로.)
            throw DomainValidationException("생성할 수 있는 항목이 없어요. 관련 경험을 추가하거나 다른 경험을 골라 주세요.")
        }
        val now = Instant.now(clock)
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = kind,
            targetSnapshot = targetSnapshot,
            templateSnapshot = templateSnapshot,
            initialSections = sections,
            createdAt = now,
        )
        val saved = artifactRepository.save(artifact)
        return mapper.toResponse(saved, templateOrigin)
    }

    /**
     * 결정적 정합화(Cycle B). 모델이 돌려준 항목을 양식/구조 불변식에 맞게 거른다. 비결정적 프롬프트 지시에만
     * 의존하지 않고 여기서 결정적으로 강제한다.
     *
     * - [MED-3] 산출물 종류와 호환되지 않는 sectionKind 항목 드롭(§371 성공 항목 보존 — 도메인 init이 전체를 던지지 않게).
     * - [MED-1] RESUME: 항목 definitionKey가 양식 스냅샷 키 집합에 없는 고아 항목 드롭(수용 기준 21/22).
     * - [포트폴리오 1:1] PORTFOLIO: definitionKey(=경험Id)가 선택 경험에 없으면 드롭, 선택 경험당 1개로 중복 제거(§357).
     */
    private fun reconcileSections(
        kind: ArtifactKind,
        material: GenerationMaterial,
        sections: List<ResolvedSection>,
    ): List<ResolvedSection> {
        val allowedKinds = allowedKindsFor(kind)
        // [MED-3] kind 불일치 항목 드롭.
        val kindCompatible = sections.filter { it.section.sectionKind in allowedKinds }
        return when (kind) {
            ArtifactKind.RESUME -> {
                // [MED-1] 양식 스냅샷 키 집합에 존재하는 항목만 보존.
                val snapshotKeys = material.templateSections.map { it.definitionKey }.toSet()
                kindCompatible.filter { it.section.definitionKey in snapshotKeys }
            }
            ArtifactKind.PORTFOLIO -> {
                // [포트폴리오 1:1] 선택 경험Id에 해당하는 항목만, 경험당 최초 1개만 보존(중복 제거).
                val selectedKeys = material.selectedExperienceIds.map { it.value.toString() }.toSet()
                val seen = mutableSetOf<String>()
                kindCompatible.filter { resolved ->
                    resolved.section.definitionKey in selectedKeys && seen.add(resolved.section.definitionKey)
                }
            }
        }
    }

    private fun allowedKindsFor(kind: ArtifactKind): Set<watson.resumaker.artifact.domain.SectionKind> = when (kind) {
        ArtifactKind.RESUME -> setOf(
            watson.resumaker.artifact.domain.SectionKind.SUMMARY,
            watson.resumaker.artifact.domain.SectionKind.CAREER,
        )
        ArtifactKind.PORTFOLIO -> setOf(watson.resumaker.artifact.domain.SectionKind.EXPERIENCE_NARRATIVE)
    }

    private fun ResolvedSection.toArtifactSection(): ArtifactSection =
        ArtifactSection.create(
            definitionKey = section.definitionKey,
            sectionKind = section.sectionKind,
            content = SectionContent.of(section.content),
            // 상태는 자동 검증·재생성 흐름이 이미 결정해 둔 값(GENERATED|GENERATION_FAILED|VALIDATION_FAILED).
            status = status,
            sourceExperienceIds = section.sourceExperienceIds,
            factGroundings = section.factGroundings.map { grounding ->
                FactGrounding.create(
                    token = FactToken.of(grounding.token),
                    kind = grounding.kind,
                    sourceExperienceId = grounding.sourceExperienceId,
                    evidenceText = grounding.evidenceText,
                )
            },
        )

    private fun toTemplateSnapshot(specs: List<TemplateSectionSpec>): TemplateSnapshot =
        TemplateSnapshot.of(
            specs.map {
                SnapshotSection.of(
                    definitionKey = it.definitionKey,
                    name = it.name,
                    sectionKind = it.sectionKind,
                    required = it.required,
                )
            },
        )

    private fun ExperienceRecord.toSnapshot(): ExperienceSnapshot = ExperienceSnapshot(
        id = id,
        title = title.value,
        body = body.value,
        situation = detail.situation,
        action = detail.action,
        result = detail.result,
        skillTags = detail.skillTags.map { it.value },
    )

    private fun TargetBrief.toSnapshot(): TargetSnapshot = TargetSnapshot(
        recruitDirection = recruitDirection.value,
        company = company?.value,
        job = job?.value,
        // 전략이 READY일 때만 싣는다(추출 전·실패·진행 중이면 원문으로 생성한다 — 어댑터가 폴백).
        writingStrategy = readyWritingStrategy(),
    )

    private fun TargetBrief.toArtifactTargetSnapshot(): ArtifactTargetSnapshot = ArtifactTargetSnapshot.of(
        recruitDirection = recruitDirection,
        company = company?.value,
        job = job?.value,
        // 생성에 쓴 전략(READY인 경우의 원본 JSON)을 산출물에 함께 불변 보존한다(원문 폴백이면 null).
        writingStrategyJson = readyWritingStrategy()?.let { writingStrategyJson },
    )

    /**
     * 전략 상태가 READY이고 JSON이 정상 역직렬화될 때만 [WritingStrategy]를 돌려준다. 그 외(미추출·실패·진행 중·
     * JSON 손상)는 null이라 생성은 채용 방향 원문으로 진행한다(어댑터 폴백).
     */
    private fun TargetBrief.readyWritingStrategy(): watson.resumaker.target.domain.WritingStrategy? {
        if (strategyStatus != watson.resumaker.target.domain.StrategyStatus.READY) return null
        val json = writingStrategyJson ?: return null
        return runCatching {
            objectMapper.readValue(json, watson.resumaker.target.domain.WritingStrategy::class.java)
        }.getOrNull()
    }

    /** tx1에서 적재한 재료 + 목표 스냅샷을 tx2로 전달하는 컨테이너. */
    private data class PreparedGeneration(
        val material: GenerationMaterial,
        val targetSnapshot: ArtifactTargetSnapshot,
    )

    /**
     * tx1에서 적재한 이력서 재료(스냅샷). [templateSections]가 null이면 양식 미지정 = AI 생성 양식 경로다
     * (섹션 구조를 tx 밖에서 채운다). 지연 로딩 경계를 넘지 않도록 엔티티가 아닌 스냅샷만 담아 tx 밖으로 나간다.
     */
    private data class LoadedResumeMaterial(
        val experiences: List<ExperienceSnapshot>,
        val target: TargetSnapshot,
        val targetSnapshot: ArtifactTargetSnapshot,
        val templateSections: List<TemplateSectionSpec>?,
    )

    private fun definitionKeyOf(index: Int, definition: SectionDefinition): String {
        // 양식은 definitionKey를 따로 갖지 않으므로(이번 사이클) 순서+이름으로 안정 키를 만든다.
        // 버전 간 항목 대응은 같은 산출물의 같은 스냅샷 키로 이뤄지므로 산출물 내 유일하면 충분하다.
        // [LOW-2] 이름이 길면 키가 MAX_KEY_LENGTH(100)를 넘어 영속 tx 내에서 throw하므로,
        // index가 이미 산출물 내 유일성을 보장하는 점을 이용해 이름 성분을 상한 이내로 잘라낸다.
        val prefix = "section-$index-"
        val nameBudget = (MAX_DEFINITION_KEY_LENGTH - prefix.length).coerceAtLeast(0)
        val safeName = definition.name.take(nameBudget)
        return prefix + safeName
    }

    private fun SectionCharacter.toSectionKind() = when (this) {
        SectionCharacter.SUMMARY -> watson.resumaker.artifact.domain.SectionKind.SUMMARY
        SectionCharacter.CAREER -> watson.resumaker.artifact.domain.SectionKind.CAREER
    }

    companion object {
        /** definitionKey 길이 상한(영속 컬럼 [ArtifactSection.MAX_KEY_LENGTH]·[SnapshotSection.MAX_KEY_LENGTH]와 일치). */
        private const val MAX_DEFINITION_KEY_LENGTH = ArtifactSection.MAX_KEY_LENGTH

        /**
         * AI 생성 양식이 사용 불가일 때의 기본 구조(폴백, §186). 신입 개발자 표준 프리셋과 동형의 최소 섹션 집합으로,
         * 막다른 길 없이 생성을 진행하게 한다(이름·성격·필수여부만 — §166 구조만).
         */
        private val DEFAULT_TEMPLATE_SECTIONS: List<SectionDefinition> = listOf(
            SectionDefinition.of("한 줄 자기소개", SectionCharacter.SUMMARY, required = true),
            SectionDefinition.of("핵심 역량", SectionCharacter.SUMMARY, required = false),
            SectionDefinition.of("주요 경력", SectionCharacter.CAREER, required = true),
        )
    }
}
