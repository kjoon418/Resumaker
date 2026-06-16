package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.ArtifactTargetSnapshot
import watson.resumaker.artifact.domain.FactGrounding
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.FactToken
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecordId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * [SectionEditService] 단위 테스트(항목 직접 편집, 도메인 이해 §5·§267·§271·§428, 수용 기준 10·19).
 *
 * 검증: 편집 성공 → 새 활성 버전·그 항목만 교체(수용 기준 10 회귀)·이전 버전 보존(19), 검증 미적용(검증 통과
 * 못 할 내용도 그대로 저장·GENERATED 유지), 출처·근거 보존, 소유 격리·미존재(산출물/항목) 404, 길이 초과 400.
 *
 * 직접 편집은 AI 비호출(§428)이므로 fake 포트·검증기·락이 없다. 단일 트랜잭션이라 TransactionTemplate도 불필요하다.
 */
class SectionEditServiceTest {

    private val artifactRepository: ArtifactRepository = mock()
    private val mapper = ArtifactReadServiceMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-16T00:00:00Z"), ZoneOffset.UTC)

    private val ownerId = UserId(UUID.randomUUID())
    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    private val service = SectionEditService(
        artifactRepository = artifactRepository,
        mapper = mapper,
        clock = clock,
    )

    private fun targetSnapshot(): ArtifactTargetSnapshot =
        ArtifactTargetSnapshot.of(recruitDirection = "백엔드 신입", company = null, job = null)

    /**
     * 두 항목(요약·경력)을 가진 이력서 산출물을 만들어, (산출물, 요약항목Id, 경력항목Id)를 돌려준다.
     * 요약 항목에는 factGrounding 1개를 심어 편집 후 비워지는지 단언할 수 있게 한다.
     */
    private fun resumeArtifact(): Triple<Artifact, SectionId, SectionId> {
        val snapshot = TemplateSnapshot.of(
            listOf(
                SnapshotSection.of("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
                SnapshotSection.of("section-1-경력", "경력", SectionKind.CAREER, required = true),
            ),
        )
        // 요약에 AI 파생 factGrounding 1개 심기(편집 후 이 근거가 비워져야 한다 — §382·§428).
        val summaryGrounding = FactGrounding.create(
            token = FactToken.of("40%"),
            kind = FactKind.NUMERIC,
            sourceExperienceId = exp1,
            evidenceText = "응답 속도를 40% 단축",
        )
        val summary = ArtifactSection.create(
            definitionKey = "section-0-요약",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("원래 요약"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = listOf(summaryGrounding),
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

    private fun command(artifactId: ArtifactId, sectionId: SectionId, content: String) =
        EditSectionContentCommand(artifactId = artifactId, sectionId = sectionId, content = content)

    private fun stubSave() {
        whenever(artifactRepository.save(any<Artifact>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun 직접_편집에_성공하면_새_활성_버전에서_그_항목만_교체되고_이전_버전이_보존된다() {
        // given (수용 기준 10·19) — 요약만 편집. 경력은 그대로 복제돼야 한다.
        val (artifact, summaryId, _) = resumeArtifact()
        val originalVersionId = artifact.activeVersion().id
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubSave()

        // when
        val response = service.editSectionContent(ownerId, command(artifact.id, summaryId, "사용자가 직접 고친 요약"))

        // then — 새 활성 버전, 요약만 교체, 경력은 원본 유지.
        assertThat(response.activeVersion.versionId).isNotEqualTo(originalVersionId.value.toString())
        val byKey = response.activeVersion.sections.associateBy { it.definitionKey }
        assertThat(byKey["section-0-요약"]!!.content).isEqualTo("사용자가 직접 고친 요약")
        assertThat(byKey["section-1-경력"]!!.content).isEqualTo("원래 경력")
        // 이전 버전 보존(수용 기준 19): 산출물에 버전이 2개, 활성은 새 버전.
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.activeVersion().id).isNotEqualTo(originalVersionId)
    }

    @Test
    fun 직접_편집_항목은_GENERATED_상태로_확정된다() {
        // given (§428 최종 결정권은 사용자) — 편집 결과는 GENERATED.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubSave()

        // when
        val response = service.editSectionContent(ownerId, command(artifact.id, summaryId, "고친 내용"))

        // then
        val summary = response.activeVersion.sections.single { it.definitionKey == "section-0-요약" }
        assertThat(summary.status).isEqualTo(SectionStatus.GENERATED)
    }

    @Test
    fun 직접_편집에는_자동_검증이_적용되지_않아_검증_통과_못할_내용도_그대로_저장된다() {
        // given (§428) — 출처 경험에 없는 수치("40%")를 직접 입력해도 검증 없이 GENERATED로 저장돼야 한다.
        // 재생성이었다면 VALIDATION_FAILED가 됐을 내용이다. 편집 경로엔 검증기 의존성 자체가 없다.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubSave()

        // when
        val response = service.editSectionContent(
            ownerId,
            command(artifact.id, summaryId, "응답 속도를 40% 단축했고 네이버에서 일했다."),
        )

        // then — 검증 미적용: 내용 그대로 보존 + GENERATED.
        val summary = response.activeVersion.sections.single { it.definitionKey == "section-0-요약" }
        assertThat(summary.content).isEqualTo("응답 속도를 40% 단축했고 네이버에서 일했다.")
        assertThat(summary.status).isEqualTo(SectionStatus.GENERATED)
    }

    @Test
    fun 직접_편집은_그_항목의_출처_경험을_보존한다() {
        // given (§428) — 편집은 AI 근거 산출이 없으므로 층위1 출처(sourceExperienceIds)는 보존한다.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubSave()

        // when
        val response = service.editSectionContent(ownerId, command(artifact.id, summaryId, "고친 요약"))

        // then — 편집 항목의 출처 경험은 원본과 동일하게 유지된다.
        val summary = response.activeVersion.sections.single { it.definitionKey == "section-0-요약" }
        assertThat(summary.sourceExperienceIds).containsExactly(exp1.value.toString())
        // factGroundings는 비워진다(§382: AI 파생 근거라 사용자 편집 내용과 더 이상 대응하지 않음).
        // resumeArtifact()의 요약에 심은 grounding 1개가 편집 후 사라져야 이 단언이 통과한다.
        assertThat(artifact.activeVersion().sections.single { it.definitionKey == "section-0-요약" }.factGroundings)
            .isEmpty()
    }

    @Test
    fun 직접_편집_후_편집_항목의_factGroundings는_비워진다() {
        // given (§382·§428) — 요약에 factGrounding 1개가 있는 산출물. 편집 후 그 근거는 사라져야 한다.
        // 만약 editSection 대신 adoptSection을 호출하면 factGroundings가 복제·보존되어 이 단언이 실패한다.
        val (artifact, summaryId, _) = resumeArtifact()
        val originalGroundingCount = artifact.activeVersion()
            .sections.single { it.definitionKey == "section-0-요약" }.factGroundings.size
        assertThat(originalGroundingCount).isEqualTo(1) // 사전 조건: grounding이 실제로 있어야 한다.
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        stubSave()

        // when
        service.editSectionContent(ownerId, command(artifact.id, summaryId, "사용자가 직접 쓴 내용"))

        // then — 새 활성 버전의 편집 항목에 factGroundings가 없다.
        val editedSection = artifact.activeVersion().sections.single { it.definitionKey == "section-0-요약" }
        assertThat(editedSection.factGroundings).isEmpty()
        // 미변경 항목(경력)의 factGroundings는 그대로 복제된다(수용 기준 10).
        val careerSection = artifact.activeVersion().sections.single { it.definitionKey == "section-1-경력" }
        assertThat(careerSection.factGroundings).isEmpty() // 경력엔 원래 grounding 없음 — 복제 경로 확인.
    }

    @Test
    fun 타인_소유이거나_미존재_산출물이면_404() {
        // given (소유 격리) — findByIdAndOwnerId가 null.
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(null)

        // when and then
        assertThatThrownBy {
            service.editSectionContent(
                ownerId,
                command(ArtifactId(UUID.randomUUID()), SectionId(UUID.randomUUID()), "내용"),
            )
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 활성_버전에_없는_항목이면_404() {
        // given — 산출물은 있지만 sectionId가 활성 버전에 없다(미존재·타인 항목 동일 404).
        val (artifact, _, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)

        // when and then
        assertThatThrownBy {
            service.editSectionContent(ownerId, command(artifact.id, SectionId(UUID.randomUUID()), "내용"))
        }.isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 내용이_길이_상한을_초과하면_400_도메인검증으로_거부된다() {
        // given — SectionContent.MAX_LENGTH 초과는 도메인 VO가 DomainValidationException(→400)으로 거부한다.
        val (artifact, summaryId, _) = resumeArtifact()
        whenever(artifactRepository.findByIdAndOwnerId(artifact.id, ownerId)).thenReturn(artifact)
        val tooLong = "가".repeat(SectionContent.MAX_LENGTH + 1)

        // when and then
        assertThatThrownBy {
            service.editSectionContent(ownerId, command(artifact.id, summaryId, tooLong))
        }.isInstanceOf(DomainValidationException::class.java)
    }
}
