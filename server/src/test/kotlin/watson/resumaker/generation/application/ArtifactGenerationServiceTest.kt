package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.ExperienceType
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.presentation.GenerationResponse
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
 * [ArtifactGenerationService] 단위 테스트. **외부 포트는 fake**([ArtifactGenerationPort] 더블) — 실제 CLI 미호출.
 *
 * 검증: 빈 경험 거부(8), 부분 실패 시 성공 보존·실패 상태(9), 이력서 양식 섹션 대응·근거0 미실체화(21·23),
 * 포트폴리오 경험당 1항목(§357), 즉시 1버전 저장·활성(7), 소유 격리, 트랜잭션 내부 DTO 변환.
 */
class ArtifactGenerationServiceTest {

    private val experienceRepository: ExperienceRecordRepository = mock()
    private val targetRepository: TargetBriefRepository = mock()
    private val templateRepository: ResumeTemplateRepository = mock()
    private val artifactRepository: ArtifactRepository = mock()
    private val quotaGuard: GenerationQuotaGuard = AllowingGenerationQuotaGuard()
    private val groundingValidator: GroundingValidator = PermissiveGroundingValidator()
    private val mapper = ArtifactGenerationServiceMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-16T00:00:00Z"), ZoneOffset.UTC)

    // 실제 TransactionTemplate + 콜백을 즉시 실행하는 더블 트랜잭션 매니저(비DB 단위 테스트).
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
    private val exp2 = ExperienceRecordId(UUID.randomUUID())

    /** 호출 시 지정한 GenerationOutput을 돌려주는 fake 포트. material도 캡처한다. */
    private class FakePort(private val output: GenerationOutput) : ArtifactGenerationPort {
        var capturedMaterial: GenerationMaterial? = null
        override fun generate(material: GenerationMaterial): GenerationOutput {
            capturedMaterial = material
            return output
        }
    }

    private fun service(port: ArtifactGenerationPort) = ArtifactGenerationService(
        experienceRepository = experienceRepository,
        targetRepository = targetRepository,
        templateRepository = templateRepository,
        artifactRepository = artifactRepository,
        generationPort = port,
        quotaGuard = quotaGuard,
        groundingValidator = groundingValidator,
        mapper = mapper,
        transactionTemplate = transactionTemplate,
        clock = clock,
    )

    private fun experienceRecord(id: ExperienceRecordId): ExperienceRecord = ExperienceRecord.retrieve(
        id = id,
        ownerId = ownerId,
        title = ExperienceTitle("경험 $id"),
        type = ExperienceType.PROJECT,
        body = ExperienceBody("본문"),
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

    private fun stubResumeMaterial() {
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it)) }
            }
        whenever(targetRepository.findByIdAndOwnerId(any(), any())).thenReturn(target())
        whenever(templateRepository.findByIdAndOwnerId(any(), any())).thenReturn(template())
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    private fun stubPortfolioMaterial() {
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it)) }
            }
        whenever(targetRepository.findByIdAndOwnerId(any(), any())).thenReturn(target())
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    private fun resumeCommand(ids: List<ExperienceRecordId> = listOf(exp1)) =
        GenerateResumeCommand(experienceIds = ids, targetId = targetId, templateId = templateId)

    @Test
    fun 빈_경험_묶음은_거부된다() {
        // given (수용 기준 8)
        val port = FakePort(GenerationOutput(emptyList()))
        whenever(targetRepository.findByIdAndOwnerId(any(), any())).thenReturn(target())

        // when and then
        assertThatThrownBy { service(port).generateResume(ownerId, resumeCommand(ids = emptyList())) }
            .isInstanceOf(DomainValidationException::class.java)
        verify(artifactRepository, never()).save(any<Artifact>())
    }

    @Test
    fun 소유하지_않은_경험은_찾을_수_없음_예외() {
        // given (소유 격리) — 배치 조회가 빈 목록(미존재/타소유)을 돌려준다.
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any())).thenReturn(emptyList())
        whenever(targetRepository.findByIdAndOwnerId(any(), any())).thenReturn(target())
        val port = FakePort(GenerationOutput(emptyList()))

        // when and then
        assertThatThrownBy { service(port).generateResume(ownerId, resumeCommand()) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 이력서_생성은_즉시_1버전을_저장하고_활성으로_둔다() {
        // given (수용 기준 7)
        stubResumeMaterial()
        val output = GenerationOutput(
            listOf(
                generated("section-0-요약", SectionKind.SUMMARY, "요약 본문", succeeded = true, sources = listOf(exp1)),
                generated("section-1-경력", SectionKind.CAREER, "경력 본문", succeeded = true, sources = listOf(exp1)),
            ),
        )
        val port = FakePort(output)

        // when
        val response: GenerationResponse = service(port).generateResume(ownerId, resumeCommand())

        // then
        assertThat(response.kind).isEqualTo(ArtifactKind.RESUME)
        assertThat(response.activeVersionId).isNotBlank()
        assertThat(response.sections).hasSize(2)
        assertThat(response.sections.map { it.definitionKey })
            .containsExactly("section-0-요약", "section-1-경력")
        verify(artifactRepository).save(any<Artifact>())
    }

    @Test
    fun 부분_실패_시_성공항목은_보존되고_실패항목은_생성실패_상태로_저장된다() {
        // given (수용 기준 9)
        stubResumeMaterial()
        val output = GenerationOutput(
            listOf(
                generated("section-0-요약", SectionKind.SUMMARY, "성공", succeeded = true, sources = listOf(exp1)),
                generated("section-1-경력", SectionKind.CAREER, "", succeeded = false, sources = listOf(exp1)),
            ),
        )
        val port = FakePort(output)

        // when
        val response = service(port).generateResume(ownerId, resumeCommand())

        // then
        val statuses = response.sections.map { it.status }
        assertThat(statuses).containsExactly(SectionStatus.GENERATED, SectionStatus.GENERATION_FAILED)
    }

    @Test
    fun 이력서_항목은_양식_섹션정의_키와_근거를_보존한다() {
        // given (수용 기준 21)
        stubResumeMaterial()
        val output = GenerationOutput(
            listOf(
                generated(
                    "section-0-요약", SectionKind.SUMMARY, "요약", succeeded = true,
                    sources = listOf(exp1),
                    groundings = listOf(
                        GeneratedFactGrounding("Kotlin", watson.resumaker.artifact.domain.FactKind.PROPER_NOUN, exp1, "본문 Kotlin"),
                    ),
                ),
            ),
        )
        val port = FakePort(output)

        // when
        val response = service(port).generateResume(ownerId, resumeCommand())

        // then
        val section = response.sections.first()
        assertThat(section.sourceExperienceIds).containsExactly(exp1.value.toString())
        assertThat(section.factGroundings).hasSize(1)
        assertThat(section.factGroundings.first().token).isEqualTo("Kotlin")
    }

    @Test
    fun 모든_항목이_미실체화되면_생성이_거부된다() {
        // given (수용 기준 23 경계 — 전 섹션 근거 0이면 만들 산출물이 없다)
        stubResumeMaterial()
        val port = FakePort(GenerationOutput(emptyList()))

        // when and then
        assertThatThrownBy { service(port).generateResume(ownerId, resumeCommand()) }
            .isInstanceOf(DomainValidationException::class.java)
        verify(artifactRepository, never()).save(any<Artifact>())
    }

    @Test
    fun 포트폴리오는_선택_경험당_1항목을_재료로_넘긴다() {
        // given (도메인 이해 §357)
        stubPortfolioMaterial()
        val output = GenerationOutput(
            listOf(
                generated(exp1.value.toString(), SectionKind.EXPERIENCE_NARRATIVE, "서사1", succeeded = true, sources = listOf(exp1)),
                generated(exp2.value.toString(), SectionKind.EXPERIENCE_NARRATIVE, "서사2", succeeded = true, sources = listOf(exp2)),
            ),
        )
        val port = FakePort(output)
        val command = GeneratePortfolioCommand(experienceIds = listOf(exp1, exp2), targetId = targetId)

        // when
        val response = service(port).generatePortfolio(ownerId, command)

        // then — 재료의 selectedExperienceIds가 선택 경험과 1:1, 응답도 2항목
        assertThat(port.capturedMaterial!!.kind).isEqualTo(GenerationKind.PORTFOLIO)
        assertThat(port.capturedMaterial!!.selectedExperienceIds).containsExactly(exp1, exp2)
        assertThat(response.kind).isEqualTo(ArtifactKind.PORTFOLIO)
        assertThat(response.sections).hasSize(2)
        assertThat(response.sections.map { it.sectionKind })
            .containsOnly(SectionKind.EXPERIENCE_NARRATIVE)
    }

    @Test
    fun 외부_포트는_이력서_재료에_양식_섹션과_경험_스냅샷을_받는다() {
        // given (트랜잭션 1단계에서 적재한 재료가 포트로 넘어가는지)
        stubResumeMaterial()
        val output = GenerationOutput(
            listOf(generated("section-0-요약", SectionKind.SUMMARY, "x", succeeded = true, sources = listOf(exp1))),
        )
        val port = FakePort(output)

        // when
        service(port).generateResume(ownerId, resumeCommand(ids = listOf(exp1, exp2)))

        // then
        val material = port.capturedMaterial!!
        assertThat(material.kind).isEqualTo(GenerationKind.RESUME)
        assertThat(material.experiences.map { it.id }).containsExactly(exp1, exp2)
        assertThat(material.templateSections.map { it.sectionKind })
            .containsExactly(SectionKind.SUMMARY, SectionKind.CAREER)
        assertThat(material.target.recruitDirection).isEqualTo("백엔드 신입")
    }

    @Test
    fun 양식_스냅샷에_없는_키_섹션은_드롭된다() {
        // given (MED-1, 수용 기준 21/22) — 양식에 없는 고아 키는 버전에 들어가면 안 된다.
        stubResumeMaterial()
        val output = GenerationOutput(
            listOf(
                generated("section-0-요약", SectionKind.SUMMARY, "요약", succeeded = true, sources = listOf(exp1)),
                generated("section-99-유령", SectionKind.SUMMARY, "고아", succeeded = true, sources = listOf(exp1)),
            ),
        )
        val port = FakePort(output)

        // when
        val response = service(port).generateResume(ownerId, resumeCommand())

        // then — 고아 키 항목은 드롭되고 양식 키 항목만 남는다.
        assertThat(response.sections.map { it.definitionKey }).containsExactly("section-0-요약")
    }

    @Test
    fun 종류에_맞지_않는_섹션이_섞여도_전체가_죽지_않고_호환_항목은_보존된다() {
        // given (MED-3, §371) — RESUME에 EXPERIENCE_NARRATIVE가 섞여 와도 호환 항목은 살아야 한다.
        stubResumeMaterial()
        val output = GenerationOutput(
            listOf(
                generated("section-0-요약", SectionKind.SUMMARY, "요약", succeeded = true, sources = listOf(exp1)),
                generated("section-1-경력", SectionKind.EXPERIENCE_NARRATIVE, "잘못된 종류", succeeded = true, sources = listOf(exp1)),
            ),
        )
        val port = FakePort(output)

        // when
        val response = service(port).generateResume(ownerId, resumeCommand())

        // then — 종류 불일치 항목은 드롭되고 호환 항목은 보존된다(전체 hard-throw 없음).
        assertThat(response.sections.map { it.definitionKey }).containsExactly("section-0-요약")
        assertThat(response.sections.map { it.sectionKind }).containsOnly(SectionKind.SUMMARY)
    }

    @Test
    fun 포트폴리오는_선택_외_경험과_중복_반환을_정합화한다() {
        // given (포트폴리오 1:1, §357) — 선택에 없는 경험Id 항목 드롭 + 경험당 1개로 중복 제거.
        stubPortfolioMaterial()
        val stray = ExperienceRecordId(UUID.randomUUID())
        val output = GenerationOutput(
            listOf(
                generated(exp1.value.toString(), SectionKind.EXPERIENCE_NARRATIVE, "서사1", succeeded = true, sources = listOf(exp1)),
                generated(exp1.value.toString(), SectionKind.EXPERIENCE_NARRATIVE, "중복", succeeded = true, sources = listOf(exp1)),
                generated(stray.value.toString(), SectionKind.EXPERIENCE_NARRATIVE, "선택외", succeeded = true, sources = listOf(stray)),
                generated(exp2.value.toString(), SectionKind.EXPERIENCE_NARRATIVE, "서사2", succeeded = true, sources = listOf(exp2)),
            ),
        )
        val port = FakePort(output)
        val command = GeneratePortfolioCommand(experienceIds = listOf(exp1, exp2), targetId = targetId)

        // when
        val response = service(port).generatePortfolio(ownerId, command)

        // then — 선택 경험당 정확히 1개(중복·선택외 제거).
        assertThat(response.sections.map { it.definitionKey })
            .containsExactly(exp1.value.toString(), exp2.value.toString())
    }

    @Test
    fun 긴_섹션명도_definitionKey_길이_상한을_넘지_않는다() {
        // given (LOW-2) — 섹션명 최댓값(100자)이면 "section-0-"(10자) 접두 때문에 키가 110자가 되어
        //   MAX_KEY_LENGTH(100)를 넘으므로, 키 생성 시 이름 성분을 잘라 상한 이내로 만들어야 한다.
        val longName = "가".repeat(100)
        val longNameTemplate = ResumeTemplate.retrieve(
            id = templateId,
            ownerId = ownerId,
            name = TemplateName("긴 이름 양식"),
            sections = listOf(SectionDefinition.of(longName, SectionCharacter.SUMMARY, required = true)),
        )
        stubResumeMaterial()
        whenever(templateRepository.findByIdAndOwnerId(any(), any())).thenReturn(longNameTemplate)

        // 키는 잘려 "section-0-" + 잘린 이름이 되며, 모델은 그 키로 항목을 돌려준다고 가정한다.
        val expectedKey = "section-0-" + longName.take(100 - "section-0-".length)
        val output = GenerationOutput(
            listOf(generated(expectedKey, SectionKind.SUMMARY, "요약", succeeded = true, sources = listOf(exp1))),
        )
        val port = FakePort(output)

        // when
        val response = service(port).generateResume(ownerId, resumeCommand())

        // then — 키 길이가 상한 이내이고 항목이 보존된다(throw 없음).
        assertThat(expectedKey.length).isLessThanOrEqualTo(100)
        assertThat(response.sections.map { it.definitionKey }).containsExactly(expectedKey)
    }

    private fun generated(
        definitionKey: String,
        kind: SectionKind,
        content: String,
        succeeded: Boolean,
        sources: List<ExperienceRecordId> = emptyList(),
        groundings: List<GeneratedFactGrounding> = emptyList(),
    ) = GeneratedSection(
        definitionKey = definitionKey,
        sectionKind = kind,
        content = content,
        succeeded = succeeded,
        sourceExperienceIds = sources,
        factGroundings = groundings,
    )
}
