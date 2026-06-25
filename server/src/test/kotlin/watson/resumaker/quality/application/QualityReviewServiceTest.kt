package watson.resumaker.quality.application

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
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
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
import watson.resumaker.generation.application.FactTokenExtractor
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.TreatmentKind
import watson.resumaker.quality.infrastructure.QualityCriteriaDictionary
import watson.resumaker.quality.infrastructure.QualityCriteriaProperties
import watson.resumaker.target.domain.RecruitDirection
import java.time.Instant
import java.util.UUID

/**
 * [QualityReviewService] 단위 테스트(품질 점검 진단). 결정적 검사기·추출기는 실제 구현을 쓰고(순수·결정적),
 * 적재 레포만 mock한다.
 *
 * 검증: QC1(결정성), QC2(약점이 많을수록 소견이 많다), QC8(소유 격리 404), QC10(포트폴리오 거절),
 * QC11(모호 수치 — 근거 있으면 AUTO_REWRITE, 없으면 SUGGESTION), QC12(범위 밖 후보 미생성).
 */
class QualityReviewServiceTest {

    private val artifactRepository: ArtifactRepository = mock()
    private val experienceRepository: ExperienceRecordRepository = mock()
    private val checks = QualityCriteriaDictionary(QualityCriteriaProperties())
    private val extractor = FactTokenExtractor()
    private val service = QualityReviewService(artifactRepository, experienceRepository, checks, extractor)

    private val ownerId = UserId(UUID.randomUUID())
    private val exp1 = ExperienceRecordId(UUID.randomUUID())

    private fun section(content: String, key: String = "section-0-요약", kind: SectionKind = SectionKind.SUMMARY) =
        ArtifactSection.create(
            definitionKey = key,
            sectionKind = kind,
            content = SectionContent.of(content),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )

    private fun resume(vararg sections: ArtifactSection): Artifact {
        val snapshot = TemplateSnapshot.of(
            sections.map { SnapshotSection.of(it.definitionKey, it.definitionKey, it.sectionKind, required = true) },
        )
        return Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = ArtifactTargetSnapshot.of(RecruitDirection("백엔드 신입"), null, null),
            templateSnapshot = snapshot,
            initialSections = sections.toList(),
            createdAt = Instant.parse("2026-06-16T00:00:00Z"),
        )
    }

    private fun portfolio(): Artifact {
        val section = ArtifactSection.create(
            definitionKey = exp1.value.toString(),
            sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
            content = SectionContent.of("서사 내용"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = emptyList(),
        )
        return Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.PORTFOLIO,
            targetSnapshot = ArtifactTargetSnapshot.of(RecruitDirection("백엔드 신입"), null, null),
            templateSnapshot = null,
            initialSections = listOf(section),
            createdAt = Instant.parse("2026-06-16T00:00:00Z"),
        )
    }

    private fun experience(body: String): ExperienceRecord = ExperienceRecord.retrieve(
        id = exp1,
        ownerId = ownerId,
        title = ExperienceTitle("경험"),
        type = ExperienceType.PROJECT,
        body = ExperienceBody(body),
        detail = ExperienceDetail.EMPTY,
    )

    @Test
    fun 약한_동사_버즈워드_수동태를_소견으로_검출한다() {
        // given — 약한 동사("담당했다")·버즈워드("열정적")·수동태("개선되었다")가 한 항목에 섞여 있다.
        val artifact = resume(section("결제 시스템을 담당했다. 열정적으로 임했고 성능이 개선되었다."))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any())).thenReturn(listOf(experience("결제 시스템 개발")))

        // when
        val report = service.review(ownerId, artifact.id.value)

        // then — 세 기준이 모두 소견으로 잡히고, 표현만 다듬는 처치라 AUTO_REWRITE.
        val criterionIds = report.findings.map { it.criterion }
        assertThat(criterionIds).contains(
            QualityCriterion.STRONG_VERB, QualityCriterion.BUZZWORD, QualityCriterion.ACTIVE_VOICE,
        )
        assertThat(report.findings.filter { it.criterion == QualityCriterion.STRONG_VERB })
            .allMatch { it.treatmentKind == TreatmentKind.AUTO_REWRITE }
    }

    @Test
    fun 같은_진단은_결정적으로_같은_결과를_낸다() {
        // given (QC1) — 같은 산출물 두 번 진단.
        val artifact = resume(section("결제를 담당했다."))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any())).thenReturn(listOf(experience("결제")))

        // when
        val first = service.review(ownerId, artifact.id.value)
        val second = service.review(ownerId, artifact.id.value)

        // then — 소견 id·기준이 동일.
        assertThat(first.findings.map { it.findingId }).isEqualTo(second.findings.map { it.findingId })
    }

    @Test
    fun 약점이_더_많은_항목이_더_많은_소견을_받는다() {
        // given (QC2) — 깨끗한 항목 vs 약점 가득한 항목.
        val clean = resume(section("결제 흐름을 새로 설계해 사용자 불편을 크게 줄였어요."))
        val dirty = resume(section("결제를 담당했다. 열정적이고 책임감 있게 참여했다. 성능이 개선되었다."))
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any())).thenReturn(listOf(experience("결제")))

        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(clean)
        val cleanReport = service.review(ownerId, clean.id.value)
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(dirty)
        val dirtyReport = service.review(ownerId, dirty.id.value)

        // then
        assertThat(dirtyReport.findings.size).isGreaterThan(cleanReport.findings.size)
    }

    @Test
    fun 모호수치는_경험에_실측값이_있으면_자동적용으로_분기한다() {
        // given (QC11) — "대용량" 규모어 + 출처 경험에 수치(500)가 있다.
        val artifact = resume(section("대용량 트래픽을 처리했다."))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenReturn(listOf(experience("초당 500건을 처리했다.")))

        // when
        val report = service.review(ownerId, artifact.id.value)

        // then — 근거 있으니 AUTO_REWRITE(객관화 후보).
        val finding = report.findings.first { it.criterion == QualityCriterion.VAGUE_METRIC }
        assertThat(finding.treatmentKind).isEqualTo(TreatmentKind.AUTO_REWRITE)
    }

    @Test
    fun 모호수치는_경험에_실측값이_없으면_보강안내로_분기한다() {
        // given (QC11·AP5 핵심) — "대용량" 규모어 + 출처 경험에 수치가 없다.
        val artifact = resume(section("대용량 트래픽을 처리했다."))
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any()))
            .thenReturn(listOf(experience("많은 트래픽을 처리했다.")))

        // when
        val report = service.review(ownerId, artifact.id.value)

        // then — 근거 없으니 SUGGESTION(텍스트 안 바꾸고 경험 보강 안내), 대상 경험 식별자 동반.
        val finding = report.findings.first { it.criterion == QualityCriterion.VAGUE_METRIC }
        assertThat(finding.treatmentKind).isEqualTo(TreatmentKind.SUGGESTION)
        assertThat(finding.suggestionGuide).isNotNull
        assertThat(finding.suggestionGuide!!.targetExperienceId).isEqualTo(exp1)
    }

    @Test
    fun 소견이_달린_항목만_이름과_내용을_담아_돌려준다() {
        // given — 약점 있는 항목(담당했다)과 깨끗한 항목(설계했어요)이 함께 있다.
        val dirty = section("결제를 담당했다.", key = "요약")
        val clean = section("결제 흐름을 새로 설계했어요.", key = "경력", kind = SectionKind.CAREER)
        val artifact = resume(dirty, clean)
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)
        whenever(experienceRepository.findAllByIdInAndOwnerId(any(), any())).thenReturn(listOf(experience("결제")))

        // when
        val report = service.review(ownerId, artifact.id.value)

        // then — 소견이 달린 '요약' 항목만 표시 맥락에 포함되고, 이름·실제 내용을 담는다(정박점).
        assertThat(report.sections).hasSize(1)
        val reviewed = report.sections.single()
        assertThat(reviewed.definitionKey).isEqualTo("요약")
        assertThat(reviewed.content).isEqualTo("결제를 담당했다.")
        assertThat(reviewed.sectionId).isEqualTo(dirty.id)
        // 모든 소견의 sectionId가 표시 맥락 항목으로 매칭된다(클라이언트 묶음 키 정합).
        assertThat(report.findings.map { it.sectionId }.toSet()).containsExactly(dirty.id)
    }

    @Test
    fun 포트폴리오_산출물은_품질_점검을_거절한다() {
        // given (QC10·QC12) — 포트폴리오는 MVP 자동 개선 대상이 아니다.
        val artifact = portfolio()
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(artifact)

        // when and then — 400(DomainValidationException), 어떤 후보도 만들지 않는다.
        assertThatThrownBy { service.review(ownerId, artifact.id.value) }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 타인_소유이거나_미존재면_404() {
        // given (QC8) — findByIdAndOwnerId가 null.
        whenever(artifactRepository.findByIdAndOwnerId(any(), any())).thenReturn(null)

        // when and then
        assertThatThrownBy { service.review(ownerId, ArtifactId(UUID.randomUUID()).value) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }
}
