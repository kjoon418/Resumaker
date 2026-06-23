package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.ExperienceType
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.target.domain.RecruitDirection
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import watson.resumaker.target.infrastructure.TargetBriefRepository
import watson.resumaker.template.domain.ResumeTemplate
import watson.resumaker.template.domain.ResumeTemplateId
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.domain.TemplateName
import watson.resumaker.template.infrastructure.ResumeTemplateRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * [ArtifactGenerationService]의 **검증실패 → 자동 1회 재생성** 흐름 통합 테스트(Cycle C, 수용 기준 18·16).
 *
 * 외부 CLI 미호출: fake 포트([ScriptedPort])와 실제 [DeterministicGroundingValidator]를 결합해 결정적으로 검증한다.
 */
class ArtifactGenerationValidationFlowTest {

    private val experienceRepository: ExperienceRecordRepository = mock()
    private val targetRepository: TargetBriefRepository = mock()
    private val templateRepository: ResumeTemplateRepository = mock()
    private val artifactRepository: ArtifactRepository = mock()
    private val validator = DeterministicGroundingValidator()
    private val mapper = ArtifactGenerationServiceMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-16T00:00:00Z"), ZoneOffset.UTC)

    private val transactionManager: PlatformTransactionManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: org.springframework.transaction.TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }
    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val ownerId = UserId(UUID.randomUUID())
    private val targetId = TargetBriefId(UUID.randomUUID())
    private val templateId = ResumeTemplateId(UUID.randomUUID())
    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    /** 검증 호출 횟수 카운터로 '자동 재시도가 차감 seam을 건드리지 않음'을 간접 증명한다. */
    private class CountingQuotaGuard : GenerationQuotaGuard {
        var checks = 0
        override fun checkInitialGeneration(ownerId: UserId) { checks++ }
        override fun recordInitialGeneration(ownerId: UserId) {}
        override fun checkRegeneration(ownerId: UserId, sectionId: watson.resumaker.artifact.domain.SectionId) {}
        override fun recordRegeneration(ownerId: UserId, sectionId: watson.resumaker.artifact.domain.SectionId) {}
        override fun checkQualityImprovement(ownerId: UserId) {}
        override fun recordQualityImprovement(ownerId: UserId) {}
    }

    /**
     * 호출 순서대로 미리 정해둔 GenerationOutput을 돌려주는 fake 포트.
     * 1차 generate는 첫 스크립트, 자동 재생성(좁힌 material) 호출은 다음 스크립트를 돌려준다.
     *
     * [LOW-6] 스크립트 수를 초과해 호출되면 마지막 스크립트를 침묵 재사용하지 않고 **명확히 예외**를 던진다
     * (테스트가 예상치 못한 추가 포트 호출을 견고하게 잡아낸다).
     */
    private class ScriptedPort(private val scripts: List<GenerationOutput>) : ArtifactGenerationPort {
        val calls = mutableListOf<GenerationMaterial>()
        override fun generate(material: GenerationMaterial): GenerationOutput {
            val index = calls.size
            check(index < scripts.size) {
                "ScriptedPort: 스크립트(${scripts.size}개)를 초과한 ${index + 1}번째 generate 호출 — 예상치 못한 포트 재호출."
            }
            calls += material
            return scripts[index]
        }
    }

    private fun service(port: ArtifactGenerationPort, quotaGuard: GenerationQuotaGuard) = ArtifactGenerationService(
        experienceRepository = experienceRepository,
        targetRepository = targetRepository,
        templateRepository = templateRepository,
        artifactRepository = artifactRepository,
        generationPort = port,
        // 이 테스트는 모두 지정 양식(templateId 있음) 경로라 AI 양식 생성기는 호출되지 않는다.
        templateGenerator = object : ResumeTemplateGenerator {
            override fun generate(material: ResumeTemplateGenerationInput) = ResumeTemplateGeneration.Unavailable
        },
        quotaGuard = quotaGuard,
        sectionRegenerationProcessor = SectionRegenerationProcessor(port, validator),
        mapper = mapper,
        transactionTemplate = transactionTemplate,
        objectMapper = com.fasterxml.jackson.databind.ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build()),
        clock = clock,
    )

    private fun experienceRecord(id: ExperienceRecordId, body: String): ExperienceRecord = ExperienceRecord.retrieve(
        id = id,
        ownerId = ownerId,
        title = ExperienceTitle("경험"),
        type = ExperienceType.PROJECT,
        body = ExperienceBody(body),
        detail = ExperienceDetail.EMPTY,
    )

    private fun target(): TargetBrief = TargetBrief.retrieve(
        id = targetId,
        ownerId = ownerId,
        recruitDirection = RecruitDirection("백엔드 신입"),
        company = null,
        job = null,
    )

    private fun template(): ResumeTemplate = ResumeTemplate.retrieve(
        id = templateId,
        ownerId = ownerId,
        name = TemplateName("내 양식"),
        sections = listOf(
            SectionDefinition.of("요약", SectionCharacter.SUMMARY, required = true),
            SectionDefinition.of("경력", SectionCharacter.CAREER, required = true),
        ),
    )

    private fun stubResumeMaterial(body: String) {
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it), body) }
            }
        whenever(targetRepository.findByIdAndOwnerId(any(), any())).thenReturn(target())
        whenever(templateRepository.findByIdAndOwnerId(any(), any())).thenReturn(template())
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    private fun resumeCommand() =
        GenerateResumeCommand(experienceIds = listOf(exp1), targetId = targetId, templateId = templateId)

    /**
     * 포트폴리오 재료 stub. 양식 없이 선택 경험당 서사 1개(1:1). 경험 본문을 [body]로 둔다.
     * 재생성 시 다른 출처로 돌려보내는 경우([sourceBodies])를 위해 id→body 매핑을 받는다.
     */
    private fun stubPortfolioMaterial(sourceBodies: Map<ExperienceRecordId, String>) {
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { uuid ->
                    val id = ExperienceRecordId(uuid)
                    experienceRecord(id, sourceBodies[id] ?: "")
                }
            }
        whenever(targetRepository.findByIdAndOwnerId(any(), any())).thenReturn(target())
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    private fun portfolioCommand(experienceIds: List<ExperienceRecordId>) =
        GeneratePortfolioCommand(experienceIds = experienceIds, targetId = targetId)

    /** 포트폴리오 항목: definitionKey=경험Id 문자열, kind=EXPERIENCE_NARRATIVE, 출처=해당 경험. */
    private fun narrative(
        exp: ExperienceRecordId,
        content: String,
        sources: List<ExperienceRecordId> = listOf(exp),
    ) = GeneratedSection(
        definitionKey = exp.value.toString(),
        sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
        content = content,
        succeeded = true,
        sourceExperienceIds = sources,
        factGroundings = emptyList(),
    )

    private fun generated(
        definitionKey: String,
        kind: SectionKind,
        content: String,
    ) = GeneratedSection(
        definitionKey = definitionKey,
        sectionKind = kind,
        content = content,
        succeeded = true,
        sourceExperienceIds = listOf(exp1),
        factGroundings = emptyList(),
    )

    @Test
    fun 검증실패_항목이_자동_재생성으로_통과하면_GENERATED가_된다() {
        // given (수용 기준 18) — 출처엔 수치 없음. 1차는 "40%" 날조(검증실패) → 재생성은 근거 있는 문장.
        stubResumeMaterial(body = "응답 속도를 줄였다.")
        val first = GenerationOutput(
            listOf(generated("section-0-요약", SectionKind.SUMMARY, "응답 속도를 40% 단축했다.")),
        )
        val regen = GenerationOutput(
            listOf(generated("section-0-요약", SectionKind.SUMMARY, "응답 속도를 단축해 경험을 개선했다.")),
        )
        val port = ScriptedPort(listOf(first, regen))
        val quota = CountingQuotaGuard()

        // when
        val response = service(port, quota).generateResume(ownerId, resumeCommand())

        // then — 자동 1회 재생성 후 통과 → GENERATED, 재생성 본문이 영속.
        val section = response.sections.single { it.definitionKey == "section-0-요약" }
        assertThat(section.status).isEqualTo(SectionStatus.GENERATED)
        assertThat(section.content).isEqualTo("응답 속도를 단축해 경험을 개선했다.")
        // 포트는 1차 + 자동 재생성 1회 = 2회 호출되고, 재생성은 해당 키로 좁혀진다.
        assertThat(port.calls).hasSize(2)
        assertThat(port.calls[1].templateSections.map { it.definitionKey }).containsExactly("section-0-요약")
        // 자동 재시도는 비용 가드레일을 차감하지 않는다(§397) — 사전 점검 1회만.
        assertThat(quota.checks).isEqualTo(1)
    }

    @Test
    fun 자동_재생성도_재실패하면_VALIDATION_FAILED로_유지된다() {
        // given — 1차·재생성 모두 근거 없는 "40%" 날조.
        stubResumeMaterial(body = "응답 속도를 줄였다.")
        val script = GenerationOutput(
            listOf(generated("section-0-요약", SectionKind.SUMMARY, "응답 속도를 40% 단축했다.")),
        )
        val port = ScriptedPort(listOf(script, script))
        val quota = CountingQuotaGuard()

        // when
        val response = service(port, quota).generateResume(ownerId, resumeCommand())

        // then — 재실패 → VALIDATION_FAILED 유지(부분 실패와 동일 회복, §429).
        val section = response.sections.single { it.definitionKey == "section-0-요약" }
        assertThat(section.status).isEqualTo(SectionStatus.VALIDATION_FAILED)
        assertThat(port.calls).hasSize(2)
        assertThat(quota.checks).isEqualTo(1)
    }

    @Test
    fun 자동_재시도는_다른_성공항목에_영향을_주지_않는다() {
        // given — 요약은 근거 있어 통과, 경력만 "40%" 날조로 검증실패 → 자동 재생성.
        stubResumeMaterial(body = "Kotlin으로 응답 속도를 줄였다.")
        val first = GenerationOutput(
            listOf(
                generated("section-0-요약", SectionKind.SUMMARY, "Kotlin으로 개발했다."),
                generated("section-1-경력", SectionKind.CAREER, "응답을 40% 단축했다."),
            ),
        )
        val regen = GenerationOutput(
            listOf(generated("section-1-경력", SectionKind.CAREER, "응답 속도를 단축했다.")),
        )
        val port = ScriptedPort(listOf(first, regen))
        val quota = CountingQuotaGuard()

        // when
        val response = service(port, quota).generateResume(ownerId, resumeCommand())

        // then — 요약은 그대로 GENERATED 유지, 경력만 재생성되어 GENERATED.
        val summary = response.sections.single { it.definitionKey == "section-0-요약" }
        val career = response.sections.single { it.definitionKey == "section-1-경력" }
        assertThat(summary.status).isEqualTo(SectionStatus.GENERATED)
        assertThat(summary.content).isEqualTo("Kotlin으로 개발했다.")
        assertThat(career.status).isEqualTo(SectionStatus.GENERATED)
        assertThat(career.content).isEqualTo("응답 속도를 단축했다.")
        // 자동 재생성은 검증실패 항목(경력)만 좁혀 1회 호출.
        assertThat(port.calls).hasSize(2)
        assertThat(port.calls[1].templateSections.map { it.definitionKey }).containsExactly("section-1-경력")
    }

    // ----- [MED-4] 포트폴리오 자동재생성 흐름(이력서와 대칭) -----

    @Test
    fun 포트폴리오_검증실패_항목이_자동_재생성으로_통과하면_GENERATED가_된다() {
        // given — 경험 출처엔 수치 없음. 1차는 "40%" 날조(검증실패) → 재생성은 근거 있는 서사.
        stubPortfolioMaterial(mapOf(exp1 to "응답 속도를 줄였다."))
        val first = GenerationOutput(listOf(narrative(exp1, "응답 속도를 40% 단축했다.")))
        val regen = GenerationOutput(listOf(narrative(exp1, "응답 속도를 단축해 경험을 개선했다.")))
        val port = ScriptedPort(listOf(first, regen))
        val quota = CountingQuotaGuard()

        // when
        val response = service(port, quota).generatePortfolio(ownerId, portfolioCommand(listOf(exp1)))

        // then — 자동 1회 재생성 후 통과 → GENERATED, 재생성 본문이 영속.
        val section = response.sections.single { it.definitionKey == exp1.value.toString() }
        assertThat(section.status).isEqualTo(SectionStatus.GENERATED)
        assertThat(section.content).isEqualTo("응답 속도를 단축해 경험을 개선했다.")
        // 포트는 1차 + 자동 재생성 1회 = 2회. 포트폴리오 재생성은 narrowed selectedExperienceIds로 좁혀진다.
        assertThat(port.calls).hasSize(2)
        assertThat(port.calls[1].selectedExperienceIds).containsExactly(exp1)
        // 자동 재시도는 비용 가드레일을 차감하지 않는다(§397) — 사전 점검 1회만.
        assertThat(quota.checks).isEqualTo(1)
    }

    @Test
    fun 포트폴리오_자동_재생성도_재실패하면_VALIDATION_FAILED로_유지된다() {
        // given — 1차·재생성 모두 근거 없는 "40%" 날조.
        stubPortfolioMaterial(mapOf(exp1 to "응답 속도를 줄였다."))
        val script = GenerationOutput(listOf(narrative(exp1, "응답 속도를 40% 단축했다.")))
        val port = ScriptedPort(listOf(script, script))
        val quota = CountingQuotaGuard()

        // when
        val response = service(port, quota).generatePortfolio(ownerId, portfolioCommand(listOf(exp1)))

        // then — 재실패 → VALIDATION_FAILED 유지(부분 실패와 동일 회복, §429).
        val section = response.sections.single { it.definitionKey == exp1.value.toString() }
        assertThat(section.status).isEqualTo(SectionStatus.VALIDATION_FAILED)
        assertThat(port.calls).hasSize(2)
        assertThat(port.calls[1].selectedExperienceIds).containsExactly(exp1)
        assertThat(quota.checks).isEqualTo(1)
    }

    // ----- [MED-5] 재생성 항목이 자기(다른) 출처로 재검증되는지 -----

    @Test
    fun 재생성_항목이_다른_출처로_돌아오면_그_출처로_재검증돼_통과한다() {
        // given — exp1 출처엔 "Kotlin" 없음(1차 날조 검증실패). 재생성은 exp2를 출처로 돌려주고, exp2 본문엔 "Kotlin"이 있다.
        //         재검증이 1차 항목의 출처(exp1)가 아니라 재생성 항목 자신의 출처(exp2)를 쓰는지 고정한다.
        val exp2 = ExperienceRecordId(UUID.randomUUID())
        stubPortfolioMaterial(
            mapOf(
                exp1 to "결제 시스템을 만들었다.",
                exp2 to "Kotlin으로 결제 시스템을 만들었다.",
            ),
        )
        val first = GenerationOutput(listOf(narrative(exp1, "Kotlin으로 구현했다.")))
        // 재생성 항목은 같은 definitionKey(exp1)지만 출처는 exp2(근거 있는 경험)로 돌아온다.
        val regen = GenerationOutput(
            listOf(narrative(exp1, "Kotlin으로 결제를 구현했다.", sources = listOf(exp2))),
        )
        val port = ScriptedPort(listOf(first, regen))
        val quota = CountingQuotaGuard()

        // when — 두 경험을 모두 선택해 material.experiences에 exp2 본문이 적재되게 한다.
        val response = service(port, quota).generatePortfolio(ownerId, portfolioCommand(listOf(exp1, exp2)))

        // then — 재검증이 재생성 항목 자신의 출처(exp2)를 사용해 "Kotlin"을 근거 확인 → GENERATED.
        val section = response.sections.single { it.definitionKey == exp1.value.toString() }
        assertThat(section.status).isEqualTo(SectionStatus.GENERATED)
        assertThat(section.content).isEqualTo("Kotlin으로 결제를 구현했다.")
    }
}
