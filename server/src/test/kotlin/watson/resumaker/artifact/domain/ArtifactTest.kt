package watson.resumaker.artifact.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.artifact.domain.FactGrounding
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.FactToken
import java.time.Instant
import java.util.UUID

class ArtifactTest {

    private val ownerId = UserId(UUID.randomUUID())
    private val baseTime = Instant.parse("2026-06-16T00:00:00Z")

    private fun snapshot(): TemplateSnapshot = TemplateSnapshot.of(
        listOf(
            SnapshotSection.of("summary", "한 줄 자기소개", SectionKind.SUMMARY, required = true),
            SnapshotSection.of("career", "주요 경력", SectionKind.CAREER, required = true),
        ),
    )

    private fun section(
        definitionKey: String,
        kind: SectionKind,
        content: String,
        status: SectionStatus = SectionStatus.GENERATED,
        sources: List<ExperienceRecordId> = emptyList(),
        groundings: List<FactGrounding> = emptyList(),
    ): ArtifactSection = ArtifactSection.create(
        definitionKey = definitionKey,
        sectionKind = kind,
        content = SectionContent.of(content),
        status = status,
        sourceExperienceIds = sources,
        factGroundings = groundings,
    )

    private fun targetSnapshot(): ArtifactTargetSnapshot =
        ArtifactTargetSnapshot.of(recruitDirection = "백엔드 신입", company = null, job = null)

    private fun resume(sections: List<ArtifactSection>, createdAt: Instant = baseTime): Artifact =
        Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = snapshot(),
            initialSections = sections,
            createdAt = createdAt,
        )

    @Test
    fun 초기_생성_시_초기_버전이_활성이고_식별자와_소유자를_가진다() {
        // when
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "요약 내용"),
                section("career", SectionKind.CAREER, "경력 내용"),
            ),
        )

        // then
        assertThat(artifact.id.value).isNotNull()
        assertThat(artifact.ownerId).isEqualTo(ownerId)
        assertThat(artifact.versions).hasSize(1)
        assertThat(artifact.activeVersion()).isEqualTo(artifact.versions.first())
        assertThat(artifact.templateSnapshot).isNotNull()
        assertThat(artifact.templateSnapshot!!.sections.map { it.definitionKey })
            .containsExactly("summary", "career")
    }

    @Test
    fun 부분_실패_항목을_포함한_초기_버전을_저장할_수_있다() {
        // when (수용 기준 9 — 도메인)
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "요약 성공", SectionStatus.GENERATED),
                section("career", SectionKind.CAREER, "", SectionStatus.GENERATION_FAILED),
            ),
        )

        // then
        val statuses = artifact.activeVersion().sections.map { it.status }
        assertThat(statuses).containsExactly(SectionStatus.GENERATED, SectionStatus.GENERATION_FAILED)
    }

    @Test
    fun 항목_채택은_새_버전을_만들고_활성을_전환하며_대상만_교체한다() {
        // given (수용 기준 19)
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약"),
                section("career", SectionKind.CAREER, "원래 경력"),
            ),
        )
        val active = artifact.activeVersion()
        val targetSection = active.sections.first { it.definitionKey == "career" }

        // when
        val newVersion = artifact.adoptSection(
            targetSection.id,
            SectionContent.of("새 경력"),
            baseTime.plusSeconds(60),
        )

        // then — 새 버전이 활성이고 버전이 2개
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.activeVersion()).isEqualTo(newVersion)
        assertThat(newVersion).isNotEqualTo(active)

        // 대상 항목만 교체
        val newCareer = newVersion.sections.first { it.definitionKey == "career" }
        assertThat(newCareer.content.value).isEqualTo("새 경력")
        // 미변경 항목은 그대로 복제(내용 불변)
        val newSummary = newVersion.sections.first { it.definitionKey == "summary" }
        assertThat(newSummary.content.value).isEqualTo("원래 요약")
    }

    @Test
    fun 채택해도_직전_버전은_불변이다() {
        // given (수용 기준 19 — 다른 항목 불변)
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약"),
                section("career", SectionKind.CAREER, "원래 경력"),
            ),
        )
        val previous = artifact.activeVersion()
        val previousCareerContent = previous.sections.first { it.definitionKey == "career" }.content.value

        // when
        artifact.adoptSection(
            previous.sections.first { it.definitionKey == "career" }.id,
            SectionContent.of("새 경력"),
            baseTime.plusSeconds(60),
        )

        // then — 직전 버전의 내용은 그대로
        val stillPrevious = artifact.versions.first { it.id == previous.id }
        assertThat(stillPrevious.sections.first { it.definitionKey == "career" }.content.value)
            .isEqualTo(previousCareerContent)
    }

    @Test
    fun 활성_버전에_없는_항목을_채택하면_거부된다() {
        // given
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "요약")))

        // when and then
        assertThatThrownBy {
            artifact.adoptSection(SectionId(UUID.randomUUID()), SectionContent.of("x"), baseTime)
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 상한_초과_시_가장_오래된_버전을_정리한다() {
        // given (수용 기준 11) — 버전 3개 생성
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v2"),
            baseTime.plusSeconds(60),
        )
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v3"),
            baseTime.plusSeconds(120),
        )
        val oldestId = artifact.versions.minByOrNull { it.createdAt }!!.id

        // when — 상한 2로 정리
        val pruned = artifact.pruneOldestIfExceeds(limit = 2)

        // then
        assertThat(pruned).hasSize(1)
        assertThat(pruned.first().id).isEqualTo(oldestId)
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.versions.map { it.id }).doesNotContain(oldestId)
    }

    @Test
    fun 상한을_여럿_초과하면_상한_이하까지_반복_정리한다() {
        // given (MEDIUM-1) — 버전 4개 생성
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v2"),
            baseTime.plusSeconds(60),
        )
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v3"),
            baseTime.plusSeconds(120),
        )
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v4"),
            baseTime.plusSeconds(180),
        )
        val activeId = artifact.activeVersion().id

        // when — 상한 2로 정리(4 → 2, 2개 제거)
        val pruned = artifact.pruneOldestIfExceeds(limit = 2)

        // then — 2개가 오래된 순으로 제거되고 활성은 보존
        assertThat(pruned).hasSize(2)
        assertThat(pruned.map { it.createdAt }).isSorted()
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.versions.map { it.id }).contains(activeId)
        assertThat(artifact.activeVersion().id).isEqualTo(activeId)
    }

    @Test
    fun 상한_1로_정리하면_활성_버전만_남고_나머지는_반복_정리된다() {
        // given (MEDIUM-1, ArtifactVersioningProperties KDoc "상한이 1이어도 불변식 유지") — 버전 3개
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v2"),
            baseTime.plusSeconds(60),
        )
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v3"),
            baseTime.plusSeconds(120),
        )
        val activeId = artifact.activeVersion().id

        // when — 상한 1로 정리(3 → 1, 비활성 2개를 오래된 순으로 반복 제거하고 활성에서 멈춤)
        val pruned = artifact.pruneOldestIfExceeds(limit = 1)

        // then — 정확히 활성만 남고, 제거분은 오래된 순 2개
        assertThat(pruned).hasSize(2)
        assertThat(pruned.map { it.createdAt }).isSorted()
        assertThat(pruned.map { it.id }).doesNotContain(activeId)
        assertThat(artifact.versions.map { it.id }).containsExactly(activeId)
        assertThat(artifact.activeVersion().id).isEqualTo(activeId)
    }

    @Test
    fun 상한은_1_미만이면_거부한다() {
        // given (pruneOldestIfExceeds 계약: limit >= 1) — 활성 보존 불변식이 무의미해지는 입력 방어
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))

        // when and then
        assertThatThrownBy {
            artifact.pruneOldestIfExceeds(limit = 0)
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 정리_시_활성_버전은_대상에서_제외된다() {
        // given (수용 기준 11 불변식) — 활성이 가장 오래된 버전이 되도록 구성
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))
        artifact.adoptSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v2"),
            baseTime.plusSeconds(60),
        )
        // 활성을 가장 오래된 v1로 되돌리기 위해 retrieve로 재구성
        val v1 = artifact.versions.minByOrNull { it.createdAt }!!
        val reconstructed = Artifact.retrieve(
            id = artifact.id,
            ownerId = ownerId,
            kind = ArtifactKind.RESUME,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = snapshot(),
            versions = artifact.versions,
            activeVersionId = v1.id,
        )

        // when — 상한 1로 정리(활성 v1은 제외, v2가 정리되어야 함)
        val pruned = reconstructed.pruneOldestIfExceeds(limit = 1)

        // then
        assertThat(pruned).hasSize(1)
        assertThat(pruned.first().id).isNotEqualTo(v1.id)
        assertThat(reconstructed.versions.map { it.id }).contains(v1.id)
        assertThat(reconstructed.activeVersion().id).isEqualTo(v1.id)
    }

    @Test
    fun 상한_이내면_정리하지_않는다() {
        // given
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))

        // when and then
        assertThat(artifact.pruneOldestIfExceeds(limit = 5)).isEmpty()
        assertThat(artifact.versions).hasSize(1)
    }

    @Test
    fun 포트폴리오는_양식_스냅샷이_없다() {
        // when
        val artifact = Artifact.create(
            ownerId = ownerId,
            kind = ArtifactKind.PORTFOLIO,
            targetSnapshot = targetSnapshot(),
            templateSnapshot = null,
            initialSections = listOf(
                section("exp-1", SectionKind.EXPERIENCE_NARRATIVE, "경험 서사"),
            ),
            createdAt = baseTime,
        )

        // then
        assertThat(artifact.templateSnapshot).isNull()
    }

    @Test
    fun 이력서인데_양식_스냅샷이_없으면_생성이_거부된다() {
        // when and then
        assertThatThrownBy {
            Artifact.create(
                ownerId = ownerId,
                kind = ArtifactKind.RESUME,
                targetSnapshot = targetSnapshot(),
                templateSnapshot = null,
                initialSections = listOf(section("summary", SectionKind.SUMMARY, "x")),
                createdAt = baseTime,
            )
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 포트폴리오인데_양식_스냅샷이_있으면_생성이_거부된다() {
        // when and then
        assertThatThrownBy {
            Artifact.create(
                ownerId = ownerId,
                kind = ArtifactKind.PORTFOLIO,
                targetSnapshot = targetSnapshot(),
                templateSnapshot = snapshot(),
                initialSections = listOf(section("exp-1", SectionKind.EXPERIENCE_NARRATIVE, "x")),
                createdAt = baseTime,
            )
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 채택해도_직전_버전_섹션의_출처_경험_목록은_영향받지_않는다() {
        // given (수용 기준 19 — sourceExperienceIds 버전 간 격리, 메모리 단언)
        val exp1 = ExperienceRecordId(UUID.randomUUID())
        val exp2 = ExperienceRecordId(UUID.randomUUID())
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약", sources = listOf(exp1)),
                section("career", SectionKind.CAREER, "원래 경력", sources = listOf(exp1, exp2)),
            ),
        )
        val previous = artifact.activeVersion()
        val previousCareerSources =
            previous.sections.first { it.definitionKey == "career" }.sourceExperienceIds

        // when — career 채택으로 새 버전 생성
        val newVersion = artifact.adoptSection(
            previous.sections.first { it.definitionKey == "career" }.id,
            SectionContent.of("새 경력"),
            baseTime.plusSeconds(60),
        )

        // then — 직전 버전의 출처 목록은 그대로(컨테이너 격리), 새 버전과 같은 값이되 다른 인스턴스
        assertThat(previous.sections.first { it.definitionKey == "career" }.sourceExperienceIds)
            .containsExactlyElementsOf(previousCareerSources)
        val newCareerSources =
            newVersion.sections.first { it.definitionKey == "career" }.sourceExperienceIds
        assertThat(newCareerSources).containsExactly(exp1, exp2)
    }

    @Test
    fun 이력서에_포트폴리오_섹션_종류가_섞이면_생성이_거부된다() {
        // when and then (LOW-4 — §166~169 정합 가드)
        assertThatThrownBy {
            resume(listOf(section("summary", SectionKind.EXPERIENCE_NARRATIVE, "x")))
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 포트폴리오에_이력서_섹션_종류가_섞이면_생성이_거부된다() {
        // when and then (LOW-4 — §166~169 정합 가드)
        assertThatThrownBy {
            Artifact.create(
                ownerId = ownerId,
                kind = ArtifactKind.PORTFOLIO,
                targetSnapshot = targetSnapshot(),
                templateSnapshot = null,
                initialSections = listOf(section("exp-1", SectionKind.SUMMARY, "x")),
                createdAt = baseTime,
            )
        }.isInstanceOf(DomainValidationException::class.java)
    }

    // ── editSection 도메인 테스트 ────────────────────────────────────────────

    private fun groundingOn(section: ArtifactSection): FactGrounding = FactGrounding.create(
        token = FactToken.of("40%"),
        kind = FactKind.NUMERIC,
        sourceExperienceId = ExperienceRecordId(UUID.randomUUID()),
        evidenceText = "응답 속도를 40% 단축",
    )

    @Test
    fun editSection은_새_버전을_만들고_편집_항목만_교체하며_이전_버전을_보존한다() {
        // given (수용 기준 10·19)
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약"),
                section("career", SectionKind.CAREER, "원래 경력"),
            ),
        )
        val active = artifact.activeVersion()
        val targetSection = active.sections.first { it.definitionKey == "summary" }

        // when
        val newVersion = artifact.editSection(targetSection.id, SectionContent.of("직접 고친 요약"), baseTime.plusSeconds(60))

        // then — 새 버전이 활성이고 버전이 2개
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.activeVersion()).isEqualTo(newVersion)
        assertThat(newVersion).isNotEqualTo(active)
        // 편집 항목만 교체
        val newSummary = newVersion.sections.first { it.definitionKey == "summary" }
        assertThat(newSummary.content.value).isEqualTo("직접 고친 요약")
        assertThat(newSummary.status).isEqualTo(SectionStatus.GENERATED)
        // 미변경 항목은 그대로 복제
        val newCareer = newVersion.sections.first { it.definitionKey == "career" }
        assertThat(newCareer.content.value).isEqualTo("원래 경력")
    }

    @Test
    fun editSection은_편집_항목의_factGroundings를_비운다() {
        // given (§382·§428) — summary에 grounding 1개 심기.
        // 만약 copyForNewVersion을 쓰면 grounding이 복제·보존되어 이 단언이 실패한다.
        val exp1 = ExperienceRecordId(UUID.randomUUID())
        val grounding = FactGrounding.create(
            token = FactToken.of("40%"),
            kind = FactKind.NUMERIC,
            sourceExperienceId = exp1,
            evidenceText = "40% 단축",
        )
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약", sources = listOf(exp1), groundings = listOf(grounding)),
                section("career", SectionKind.CAREER, "원래 경력"),
            ),
        )
        val summarySection = artifact.activeVersion().sections.first { it.definitionKey == "summary" }
        assertThat(summarySection.factGroundings).hasSize(1) // 사전 조건

        // when
        val newVersion = artifact.editSection(summarySection.id, SectionContent.of("고친 요약"), baseTime.plusSeconds(60))

        // then — 편집 항목의 factGroundings는 비어 있다.
        val newSummary = newVersion.sections.first { it.definitionKey == "summary" }
        assertThat(newSummary.factGroundings).isEmpty()
    }

    @Test
    fun editSection은_편집_항목의_sourceExperienceIds를_보존한다() {
        // given (§428) — "근거 없이 만들어진 항목 0건" 불변식 유지.
        val exp1 = ExperienceRecordId(UUID.randomUUID())
        val exp2 = ExperienceRecordId(UUID.randomUUID())
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약", sources = listOf(exp1, exp2)),
                section("career", SectionKind.CAREER, "원래 경력"),
            ),
        )
        val summarySection = artifact.activeVersion().sections.first { it.definitionKey == "summary" }

        // when
        val newVersion = artifact.editSection(summarySection.id, SectionContent.of("고친 요약"), baseTime.plusSeconds(60))

        // then — 편집 항목의 출처 경험은 그대로 보존된다.
        val newSummary = newVersion.sections.first { it.definitionKey == "summary" }
        assertThat(newSummary.sourceExperienceIds).containsExactly(exp1, exp2)
    }

    @Test
    fun editSection은_미변경_항목의_factGroundings를_그대로_복제한다() {
        // given (수용 기준 10) — career에 grounding 1개. summary만 편집 → career grounding 보존.
        val exp1 = ExperienceRecordId(UUID.randomUUID())
        val grounding = FactGrounding.create(
            token = FactToken.of("네이버"),
            kind = FactKind.PROPER_NOUN,
            sourceExperienceId = exp1,
            evidenceText = "네이버 인턴",
        )
        val artifact = resume(
            listOf(
                section("summary", SectionKind.SUMMARY, "원래 요약"),
                section("career", SectionKind.CAREER, "원래 경력", sources = listOf(exp1), groundings = listOf(grounding)),
            ),
        )
        val summarySection = artifact.activeVersion().sections.first { it.definitionKey == "summary" }

        // when — summary만 편집
        val newVersion = artifact.editSection(summarySection.id, SectionContent.of("고친 요약"), baseTime.plusSeconds(60))

        // then — 미변경 career의 factGroundings는 복제되어 1개 유지된다.
        val newCareer = newVersion.sections.first { it.definitionKey == "career" }
        assertThat(newCareer.factGroundings).hasSize(1)
        assertThat(newCareer.factGroundings.first().tokenValue).isEqualTo("네이버")
    }

    @Test
    fun editSection은_활성_버전에_없는_항목을_편집하면_거부된다() {
        // given
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "요약")))

        // when and then
        assertThatThrownBy {
            artifact.editSection(SectionId(UUID.randomUUID()), SectionContent.of("x"), baseTime)
        }.isInstanceOf(DomainValidationException::class.java)
    }

    // ── restoreVersion 도메인 테스트 ─────────────────────────────────────────

    @Test
    fun restoreVersion은_고른_이전_버전을_활성으로_전환하고_새_버전을_만들지_않는다() {
        // given (§277·§283, "복원 = 활성 전환") — 편집으로 v2를 만들어 활성이 v2가 된 상태.
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))
        val v1Id = artifact.activeVersion().id
        artifact.editSection(
            artifact.activeVersion().sections.first().id,
            SectionContent.of("v2"),
            baseTime.plusSeconds(60),
        )
        val v2Id = artifact.activeVersion().id
        assertThat(v2Id).isNotEqualTo(v1Id)

        // when — v1으로 복원
        val restored = artifact.restoreVersion(v1Id)

        // then — 활성이 v1으로 전환되고, 버전 수는 그대로(새 버전 미생성). v2도 보존된다.
        assertThat(restored.id).isEqualTo(v1Id)
        assertThat(artifact.activeVersion().id).isEqualTo(v1Id)
        assertThat(artifact.versions).hasSize(2)
        assertThat(artifact.versions.map { it.id }).contains(v2Id)
        assertThat(artifact.activeVersion().sections.first().content.value).isEqualTo("v1")
    }

    @Test
    fun restoreVersion은_이미_활성인_버전으로_복원해도_안전하다() {
        // given — 현재 활성 버전으로 복원하는 멱등 케이스.
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))
        val activeId = artifact.activeVersion().id

        // when
        val restored = artifact.restoreVersion(activeId)

        // then
        assertThat(restored.id).isEqualTo(activeId)
        assertThat(artifact.activeVersion().id).isEqualTo(activeId)
        assertThat(artifact.versions).hasSize(1)
    }

    @Test
    fun restoreVersion은_이_산출물에_없는_버전으로_복원하면_거부된다() {
        // given
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))

        // when and then
        assertThatThrownBy {
            artifact.restoreVersion(VersionId(UUID.randomUUID()))
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 활성_버전_식별자가_버전_목록에_없으면_복원이_거부된다() {
        // given
        val artifact = resume(listOf(section("summary", SectionKind.SUMMARY, "v1")))

        // when and then
        assertThatThrownBy {
            Artifact.retrieve(
                id = artifact.id,
                ownerId = ownerId,
                kind = ArtifactKind.RESUME,
                targetSnapshot = targetSnapshot(),
                templateSnapshot = snapshot(),
                versions = artifact.versions,
                activeVersionId = VersionId(UUID.randomUUID()),
            )
        }.isInstanceOf(DomainValidationException::class.java)
    }
}
