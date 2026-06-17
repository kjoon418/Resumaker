package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceBody
import watson.resumaker.experience.domain.ExperienceDetail
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.domain.ExperienceTitle
import watson.resumaker.experience.domain.ExperienceType
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.infrastructure.ArtifactVersioningProperties
import watson.resumaker.generation.presentation.ArtifactResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * [SectionRegenerationService] 단위 테스트(항목 단위 재생성, 수용 기준 10·19·20). **외부 CLI 미호출** — fake 포트.
 *
 * 검증: 재생성 성공 → 새 활성 버전·해당 항목만 교체(수용 기준 10 회귀)·이전 버전 보존(19), 개선 지시 전달,
 * 검증실패 → 자동 재생성 → VALIDATION_FAILED 보존(§429), 동시 중복 재생성 거절(20), 소유 격리·미존재 404.
 */
class SectionRegenerationServiceTest {

    private val artifactRepository: ArtifactRepository = mock()
    private val experienceRepository: ExperienceRecordRepository = mock()

    /**
     * 차감·점검 호출을 기록하는 fake 가드. 기본은 항상 통과(기존 경로 그린 유지)하되, [blockCheck]를 켜면
     * 점검 시 [QuotaExceededException]을 던져 상한 차단을 시뮬레이트한다. 차감이 정확한 지점에서 일어나는지 검증한다.
     */
    private class RecordingQuotaGuard : GenerationQuotaGuard {
        var blockCheck = false
        var regenerationChecked = 0
        var regenerationRecorded = 0
        override fun checkInitialGeneration(ownerId: UserId) {}
        override fun recordInitialGeneration(ownerId: UserId) {}
        override fun checkRegeneration(ownerId: UserId, sectionId: SectionId) {
            regenerationChecked++
            if (blockCheck) {
                throw watson.resumaker.common.domain.QuotaExceededException(
                    message = "한도 초과",
                    code = "REGENERATION_QUOTA_EXCEEDED",
                    action = "EDIT_MANUALLY",
                )
            }
        }
        override fun recordRegeneration(ownerId: UserId, sectionId: SectionId) { regenerationRecorded++ }
    }

    private val quotaGuard = RecordingQuotaGuard()
    private val locks = SectionRegenerationLocks()
    private val mapper = ArtifactReadServiceMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-16T00:00:00Z"), ZoneOffset.UTC)

    private val transactionManager: PlatformTransactionManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: org.springframework.transaction.TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }
    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val ownerId = UserId(UUID.randomUUID())
    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    /** 호출 시 지정 출력을 돌려주고 material을 캡처하는 fake 포트. */
    private class FakePort(private val output: GenerationOutput) : ArtifactGenerationPort {
        val calls = mutableListOf<GenerationMaterial>()
        override fun generate(material: GenerationMaterial): GenerationOutput {
            calls += material
            return output
        }
    }

    /** 호출 순서대로 스크립트를 돌려주는 fake 포트(검증실패 자동 재생성 검증용). */
    private class ScriptedPort(private val scripts: List<GenerationOutput>) : ArtifactGenerationPort {
        val calls = mutableListOf<GenerationMaterial>()
        override fun generate(material: GenerationMaterial): GenerationOutput {
            val index = calls.size
            check(index < scripts.size) { "ScriptedPort: 스크립트 초과 호출 ${index + 1}." }
            calls += material
            return scripts[index]
        }
    }

    private fun service(
        port: ArtifactGenerationPort,
        validator: GroundingValidator = PermissiveGroundingValidator(),
        versionRetentionLimit: Int = 10,
    ) =
        SectionRegenerationService(
            artifactRepository = artifactRepository,
            experienceRepository = experienceRepository,
            generationPort = port,
            processor = SectionRegenerationProcessor(port, validator),
            quotaGuard = quotaGuard,
            locks = locks,
            mapper = mapper,
            versioningProperties = ArtifactVersioningProperties(
                versionRetentionLimit = versionRetentionLimit,
            ),
            transactionTemplate = transactionTemplate,
            clock = clock,
        )

    private fun experienceRecord(id: ExperienceRecordId, body: String = "본문"): ExperienceRecord =
        ExperienceRecord.retrieve(
            id = id,
            ownerId = ownerId,
            title = ExperienceTitle("경험"),
            type = ExperienceType.PROJECT,
            body = ExperienceBody(body),
            detail = ExperienceDetail.EMPTY,
        )

    private fun targetSnapshot(): ArtifactTargetSnapshot =
        ArtifactTargetSnapshot.of(recruitDirection = "백엔드 신입", company = null, job = null)

    /** 두 항목(요약·경력)을 가진 이력서 산출물을 만들어, (산출물, 요약항목Id, 경력항목Id)를 돌려준다. */
    private fun resumeArtifact(): Triple<Artifact, SectionId, SectionId> {
        val snapshot = TemplateSnapshot.of(
            listOf(
                SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
                SnapshotSection.of("section-1-경력", "경력", SectionKind.CAREER, required = true),
            ),
        )
        val summary = ArtifactSection.create(
            definitionKey = "section-0-요약",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("원래 요약"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        val career = ArtifactSection.create(
            definitionKey = "section-1-경력",
            sectionKind = SectionKind.CAREER,
            content = SectionContent.of("원래 경력"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = snapshot,
            initialSections = listOf(summary, career),
            createdAt = Instant.parse("2026-06-15T00:00:00Z"),
        )
        return Triple(artifact, summary.id, career.id)
    }

    private fun generated(
        definitionKey: String,
        kind: SectionKind,
        content: String,
        succeeded: Boolean = true,
        sources: List<ExperienceRecordId> = listOf(exp1),
    ) = GeneratedSection(
        definitionKey = definitionKey,
        sectionKind = kind,
        content = content,
        succeeded = succeeded,
        sourceExperienceIds = sources,
        factGroundings = emptyList(),
    )

    private fun command(artifactId: ArtifactId, sectionId: SectionId, directive: String? = null) =
        RegenerateSectionCommand(artifactId = artifactId, sectionId = sectionId, directive = directive)

    private fun stubLoads() {
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it)) }
            }
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 재생성에_성공하면_새_활성_버전에서_그_항목만_교체되고_이전_버전이_보존된다() {
        // given (수용 기준 10·19) — 요약만 재생성. 경력은 그대로 복제돼야 한다.
        val (artifact, summaryId, _) = resumeArtifact()
        val originalVersionId = artifact.activeVersion().id
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시 만든 요약"))))

        // when
        val response = service(port).regenerateSection(ownerId, command(artifact.id, summaryId))

        // then — 새 활성 버전, 요약만 교체, 경력은 원본 유지.
        assertThat(response.activeVersion.versionId).isNotEqualTo(originalVersionId.value.toString())
        val byKey = response.activeVersion.sections.associateBy { it.definitionKey }
        assertThat(byKey["section-0-요약"]!!.content).isEqualTo("다시 만든 요약")
        assertThat(byKey["section-1-경력"]!!.content).isEqualTo("원래 경력")
        // 이전 버전 보존(수용 기준 19): 산출물에 버전이 2개, 활성은 새 버전.
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.activeVersion().id).isNotEqualTo(originalVersionId)
        verify(artifactRepository).save(any<Artifact>())
    }

    @Test
    fun 재생성_반복으로_상한을_넘으면_가장_오래된_비활성_버전부터_정리되고_활성은_보존된다() {
        // given (수용 기준 11) — 보관 상한 2. 반복 재생성으로 버전이 늘면 상한 이하로 정리되고 활성은 남는다.
        val (artifact, _, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시 만든 요약"))))
        val limitedService = service(port, versionRetentionLimit = 2)

        // when — 초기 1버전에서 3회 재생성(재생성은 새 활성 버전을 추가). 매번 활성 요약 항목을 다시 잡는다.
        var lastResponse: ArtifactResponse? = null
        repeat(3) {
            val summaryId = artifact.activeVersion().sections.single { it.definitionKey == "section-0-요약" }.id
            lastResponse = limitedService.regenerateSection(ownerId, command(artifact.id, summaryId))
        }

        // then — 상한 2 이하로 정리되고 활성(가장 최근 재생성)은 보존된다.
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.versions.map { it.id }).contains(artifact.activeVersion().id)
        // 마지막 재생성 응답은 직전 정리 1건을 고지한다(3→2 정리 1건).
        assertThat(lastResponse!!.prunedVersionCount).isEqualTo(1)
    }

    @Test
    fun 상한_이내면_재생성해도_정리하지_않는다() {
        // given (수용 기준 11) — 상한 10(기본). 1회 재생성은 2버전이라 정리 없음, prunedVersionCount=0.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시 만든 요약"))))

        // when
        val response = service(port).regenerateSection(ownerId, command(artifact.id, summaryId))

        // then
        assertThat(artifact.versions).hasSize(2)
        assertThat(response.prunedVersionCount).isEqualTo(0)
    }

    @Test
    fun 개선_지시가_포트_재료에_전달된다() {
        // given (§268) — directive가 GenerationMaterial로 전달돼야 한다.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "더 짧은 요약"))))

        // when
        service(port).regenerateSection(ownerId, command(artifact.id, summaryId, directive = "더 짧게"))

        // then
        assertThat(port.calls.first().directive).isEqualTo("더 짧게")
        // 재생성 재료는 그 항목 하나로 좁혀진다(다른 섹션 미산출).
        assertThat(port.calls.first().templateSections.map { it.definitionKey }).containsExactly("section-0-요약")
    }

    @Test
    fun 재생성_검증실패가_자동재생성도_재실패하면_VALIDATION_FAILED로_보존된다() {
        // given (§429) — 출처엔 수치 없음. 1차·자동재생성 모두 "40%" 날조 → 재실패 → VALIDATION_FAILED.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it), body = "응답 속도를 줄였다.") }
            }
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
        val script = GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "응답 속도를 40% 단축했다.")))
        val port = ScriptedPort(listOf(script, script))

        // when
        val response = service(port, DeterministicGroundingValidator())
            .regenerateSection(ownerId, command(artifact.id, summaryId))

        // then — 자동 1회 재생성까지 포함해 포트 2회 호출, 결과는 VALIDATION_FAILED로 새 버전에 보존.
        assertThat(port.calls).hasSize(2)
        val summary = response.activeVersion.sections.single { it.definitionKey == "section-0-요약" }
        assertThat(summary.status).isEqualTo(SectionStatus.VALIDATION_FAILED)
        // 재생성 본문은 보존된다(부분 실패와 동일 회복).
        assertThat(summary.content).isEqualTo("응답 속도를 40% 단축했다.")
    }

    @Test
    fun 같은_항목_재생성이_진행중이면_중복_요청은_거절된다() {
        // given (수용 기준 20) — 락을 먼저 점유해 '진행 중' 상태를 만든다.
        val (artifact, summaryId, _) = resumeArtifact()
        locks.tryAcquire(summaryId)
        val port = FakePort(GenerationOutput(emptyList()))

        // when and then — 중복 요청은 ConflictException으로 거절, 포트·저장 미호출.
        assertThatThrownBy { service(port).regenerateSection(ownerId, command(artifact.id, summaryId)) }
            .isInstanceOf(ConflictException::class.java)
        assertThat(port.calls).isEmpty()
        verify(artifactRepository, never()).save(any<Artifact>())
    }

    @Test
    fun 서로_다른_항목은_병렬_재생성이_허용된다() {
        // given (수용 기준 20 — 서로 다른 항목은 병렬) — 경력 항목 락을 점유해도 요약은 재생성 가능.
        val (artifact, summaryId, careerId) = resumeArtifact()
        locks.tryAcquire(careerId)
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시 만든 요약"))))

        // when — 다른 항목(경력)이 진행 중이어도 요약 재생성은 통과한다.
        val response = service(port).regenerateSection(ownerId, command(artifact.id, summaryId))

        // then
        assertThat(response.activeVersion.sections.single { it.definitionKey == "section-0-요약" }.content)
            .isEqualTo("다시 만든 요약")
    }

    @Test
    fun 재생성_성공_후_점유가_해제되어_다시_재생성할_수_있다() {
        // given — 한 번 재생성 후 같은 항목을 다시 재생성해도 거절되지 않아야 한다(finally 해제).
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시"))))

        // when
        service(port).regenerateSection(ownerId, command(artifact.id, summaryId))

        // then — 해제됐으므로 같은 항목 재점유 성공.
        assertThat(locks.tryAcquire(summaryId)).isTrue()
    }

    @Test
    fun 재생성_포트_재료에_산출물_목표_스냅샷이_사용된다() {
        // given (§347·§364) — 산출물에 저장된 목표 스냅샷(채용방향·회사·직무)이 프롬프트 재료의 target으로 전달돼야 한다.
        val targetSnap = ArtifactTargetSnapshot.of("프론트엔드 시니어", "카카오", "웹 개발자")
        val snapshot = TemplateSnapshot.of(
            listOf(SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
        )
        val summary = ArtifactSection.create(
            definitionKey = "section-0-요약",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("원래 요약"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = targetSnap,
            templateSnapshot = snapshot,
            initialSections = listOf(summary),
            createdAt = Instant.parse("2026-06-15T00:00:00Z"),
        )
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "새 요약"))))

        // when
        service(port).regenerateSection(ownerId, command(artifact.id, summary.id))

        // then — 포트 재료의 target이 산출물 목표 스냅샷과 일치한다(요청 본문에서 오지 않음).
        val capturedTarget = port.calls.first().target
        assertThat(capturedTarget.recruitDirection).isEqualTo("프론트엔드 시니어")
        assertThat(capturedTarget.company).isEqualTo("카카오")
        assertThat(capturedTarget.job).isEqualTo("웹 개발자")
    }

    @Test
    fun 목표_스냅샷은_원본_target_변경과_무관하게_불변이다() {
        // given (§347 불변 보장) — 산출물 생성 시점 목표를 스냅샷으로 복제하므로 원본이 변경돼도 영향 없음.
        // ArtifactTargetSnapshot은 생성 후 변경 수단이 없는 순수 VO다.
        val snap = ArtifactTargetSnapshot.of("백엔드 신입", "네이버", "서버 개발자")

        // when — 같은 값의 다른 스냅샷을 만들어 동등성 확인(불변 VO 동형).
        val snap2 = ArtifactTargetSnapshot.of("백엔드 신입", "네이버", "서버 개발자")

        // then
        assertThat(snap).isEqualTo(snap2)
        assertThat(snap.recruitDirection).isEqualTo("백엔드 신입")
        assertThat(snap.company).isEqualTo("네이버")
        assertThat(snap.job).isEqualTo("서버 개발자")
        // 빈 채용방향은 불변식 위반 — VO 자체가 불법 상태를 거부한다.
        assertThatThrownBy { ArtifactTargetSnapshot.of("", null, null) }
            .isInstanceOf(watson.resumaker.common.domain.DomainValidationException::class.java)
    }

    @Test
    fun 타인_소유이거나_미존재_산출물이면_404() {
        // given (소유 격리) — findByIdAndOwnerId가 null.
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(null)
        val port = FakePort(GenerationOutput(emptyList()))

        // when and then
        assertThatThrownBy {
            service(port).regenerateSection(ownerId, command(ArtifactId(UUID.randomUUID()), SectionId(UUID.randomUUID())))
        }.isInstanceOf(ResourceNotFoundException::class.java)
        assertThat(port.calls).isEmpty()
    }

    @Test
    fun 활성_버전에_없는_항목이면_404() {
        // given — 산출물은 있지만 sectionId가 활성 버전에 없다.
        val (artifact, _, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        val port = FakePort(GenerationOutput(emptyList()))

        // when and then
        assertThatThrownBy {
            service(port).regenerateSection(ownerId, command(artifact.id, SectionId(UUID.randomUUID())))
        }.isInstanceOf(ResourceNotFoundException::class.java)
        assertThat(port.calls).isEmpty()
    }

    @Test
    fun 포트폴리오_항목_재생성이_그_항목만_교체하고_이전_버전을_보존한다() {
        // given (Open Question — §357 definitionKey=경험id 1:1 불변식 가드).
        // 포트폴리오는 definitionKey == 경험Id.toString(). 재생성은 해당 경험 항목만 교체해야 한다.
        val exp2 = ExperienceRecordId(UUID.randomUUID())
        val narrativeKey1 = exp1.value.toString()
        val narrativeKey2 = exp2.value.toString()

        val section1 = ArtifactSection.create(
            definitionKey = narrativeKey1,
            sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
            content = SectionContent.of("원래 서사1"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        val section2 = ArtifactSection.create(
            definitionKey = narrativeKey2,
            sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
            content = SectionContent.of("원래 서사2"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp2),
            factGroundings = emptyList(),
        )
        val portfolio = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.PORTFOLIO,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = null,
            initialSections = listOf(section1, section2),
            createdAt = Instant.parse("2026-06-15T00:00:00Z"),
        )
        val originalVersionId = portfolio.activeVersion().id
        whenever(artifactRepository.findByIdAndOwnerId(portfolio.id, ownerId)).thenReturn(portfolio)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it)) }
            }
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }

        // 포트가 재생성된 항목(section1 키)만 돌려준다.
        val port = FakePort(
            GenerationOutput(
                listOf(
                    generated(narrativeKey1, SectionKind.EXPERIENCE_NARRATIVE, "새 서사1", sources = listOf(exp1)),
                ),
            ),
        )

        // when — section1(exp1 서사) 재생성.
        val response = service(port).regenerateSection(ownerId, command(portfolio.id, section1.id))

        // then — 새 활성 버전, section1만 교체, section2는 원본 유지(수용 기준 10).
        assertThat(response.activeVersion.versionId).isNotEqualTo(originalVersionId.value.toString())
        val byKey = response.activeVersion.sections.associateBy { it.definitionKey }
        assertThat(byKey[narrativeKey1]!!.content).isEqualTo("새 서사1")
        assertThat(byKey[narrativeKey2]!!.content).isEqualTo("원래 서사2")
        // 이전 버전 보존(수용 기준 19).
        assertThat(portfolio.versions).hasSize(2)
        assertThat(portfolio.activeVersion().id).isNotEqualTo(originalVersionId)
    }

    @Test
    fun 재생성_최종_성공_시_항목당_한도를_정확히_1회_차감한다() {
        // given (수용 기준 15, §397) — GENERATED 성공 → 항목당 1회 차감, 외부 호출 전 1회 점검.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시 만든 요약"))))

        // when
        service(port).regenerateSection(ownerId, command(artifact.id, summaryId))

        // then
        assertThat(quotaGuard.regenerationChecked).isEqualTo(1)
        assertThat(quotaGuard.regenerationRecorded).isEqualTo(1)
    }

    @Test
    fun 재생성_점검이_상한이면_외부_호출_없이_차단되고_차감하지_않는다() {
        // given (수용 기준 15) — 점검에서 QuotaExceededException → 포트 미호출·저장 미호출·차감 0.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubLoads()
        quotaGuard.blockCheck = true
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "다시"))))

        // when and then
        assertThatThrownBy { service(port).regenerateSection(ownerId, command(artifact.id, summaryId)) }
            .isInstanceOf(watson.resumaker.common.domain.QuotaExceededException::class.java)
        assertThat(port.calls).isEmpty()
        verify(artifactRepository, never()).save(any<Artifact>())
        assertThat(quotaGuard.regenerationRecorded).isEqualTo(0)
    }

    @Test
    fun 재생성_검증실패면_차감하지_않는다() {
        // given (§397 — 검증실패는 차감 대상 아님) — 자동 재시도까지 재실패해 VALIDATION_FAILED로 보존 → 미차감.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it), body = "응답 속도를 줄였다.") }
            }
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
        val script = GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "응답 속도를 40% 단축했다.")))
        val port = ScriptedPort(listOf(script, script))

        // when
        service(port, DeterministicGroundingValidator())
            .regenerateSection(ownerId, command(artifact.id, summaryId))

        // then — 점검은 했지만(빠른 실패용) 최종 성공이 아니므로 차감은 0.
        assertThat(quotaGuard.regenerationChecked).isEqualTo(1)
        assertThat(quotaGuard.regenerationRecorded).isEqualTo(0)
    }

    @Test
    fun tx1과_tx2_사이에_항목이_소멸하면_400을_반환한다() {
        // given (MEDIUM #2) — tx1에서 찾은 sectionId가 tx2 reload 시점에는 활성 버전에 없는 상황.
        // 다른 경로(채택·버전 정리 등)가 tx1→tx2 사이에 활성 버전을 바꾸면 adoptSection이 이 경로로 throw한다.
        val (artifact, summaryId, _) = resumeArtifact()

        // tx1에서는 artifact 정상 반환, tx2에서는 summaryId가 없는 다른 artifact 반환(sectionId 소멸 시뮬레이션).
        val artifactWithoutSection = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = TemplateSnapshot.of(
                listOf(SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
            ),
            initialSections = listOf(
                ArtifactSection.create(
                    definitionKey = "section-0-요약",
                    sectionKind = SectionKind.SUMMARY,
                    content = SectionContent.of("다른 버전"),
                    status = SectionStatus.GENERATED,
                    // 다른 SectionId가 할당되어 summaryId는 없다.
                    sourceExperienceIds = listOf(exp1),
                    factGroundings = emptyList(),
                ),
            ),
            createdAt = Instant.parse("2026-06-15T00:00:00Z"),
        )
        var callCount = 0
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenAnswer {
            callCount++
            // tx1(1번째 호출)은 정상 artifact, tx2(2번째 호출)는 sectionId 없는 artifact.
            if (callCount == 1) artifact else artifactWithoutSection
        }
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                ids.map { experienceRecord(ExperienceRecordId(it)) }
            }
        val port = FakePort(GenerationOutput(listOf(generated("section-0-요약", SectionKind.SUMMARY, "새 요약"))))

        // when and then — adoptSection이 DomainValidationException을 throw → 서비스 밖으로 전파(→ 400).
        assertThatThrownBy {
            service(port).regenerateSection(ownerId, command(artifact.id, summaryId))
        }.isInstanceOf(watson.resumaker.common.domain.DomainValidationException::class.java)
    }
}
