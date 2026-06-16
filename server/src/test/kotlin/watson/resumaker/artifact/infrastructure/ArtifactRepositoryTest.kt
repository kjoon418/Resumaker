package watson.resumaker.artifact.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.FactGrounding
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.FactToken
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.artifact.domain.SnapshotSection
import watson.resumaker.artifact.domain.TemplateSnapshot
import watson.resumaker.experience.domain.ExperienceRecordId
import java.time.Instant
import java.util.UUID

@DataJpaTest
class ArtifactRepositoryTest {

    @Autowired
    private lateinit var repository: ArtifactRepository

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())
    private val baseTime = Instant.parse("2026-06-16T00:00:00Z")

    private fun snapshot(): TemplateSnapshot = TemplateSnapshot.of(
        listOf(
            SnapshotSection.of("summary", "한 줄 자기소개", SectionKind.SUMMARY, required = true),
            SnapshotSection.of("career", "주요 경력", SectionKind.CAREER, required = true),
        ),
    )

    private fun resume(owner: UserId): Artifact {
        val exp1 = ExperienceRecordId(UUID.randomUUID())
        val exp2 = ExperienceRecordId(UUID.randomUUID())
        val summary = ArtifactSection.create(
            definitionKey = "summary",
            sectionKind = SectionKind.SUMMARY,
            content = SectionContent.of("요약 내용"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1, exp2),
            factGroundings = emptyList(),
        )
        val career = ArtifactSection.create(
            definitionKey = "career",
            sectionKind = SectionKind.CAREER,
            content = SectionContent.of("응답 지연을 40% 줄였다"),
            status = SectionStatus.GENERATED,
            sourceExperienceIds = listOf(exp1),
            factGroundings = listOf(
                FactGrounding.create(FactToken.of("40%"), FactKind.NUMERIC, exp1, "40% 개선"),
                FactGrounding.create(FactToken.of("Redis"), FactKind.PROPER_NOUN, exp1, "Redis 도입"),
            ),
        )
        return Artifact.create(
            ownerId = owner,
            kind = ArtifactKind.RESUME,
            templateSnapshot = snapshot(),
            initialSections = listOf(summary, career),
            createdAt = baseTime,
        )
    }

    @Test
    fun 산출물을_저장하고_버전_섹션_근거_순서를_그대로_복원한다() {
        // given
        val artifact = resume(ownerId)

        // when
        val saved = repository.saveAndFlush(artifact)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.kind).isEqualTo(ArtifactKind.RESUME)
        assertThat(found.versions).hasSize(1)
        assertThat(found.activeVersion().id).isEqualTo(found.versions.first().id)

        // 양식 스냅샷 순서 보존
        assertThat(found.templateSnapshot).isNotNull
        assertThat(found.templateSnapshot!!.sections.map { it.definitionKey })
            .containsExactly("summary", "career")

        // 섹션 순서 보존
        val sections = found.activeVersion().sections
        assertThat(sections.map { it.definitionKey }).containsExactly("summary", "career")

        // 항목 출처 경험(층위1) 순서 보존
        assertThat(sections.first().sourceExperienceIds).hasSize(2)

        // 사실 근거(층위2) 순서 보존
        val groundings = sections.first { it.definitionKey == "career" }.factGroundings
        assertThat(groundings.map { it.token.value }).containsExactly("40%", "Redis")
        assertThat(groundings.map { it.kind }).containsExactly(FactKind.NUMERIC, FactKind.PROPER_NOUN)
    }

    @Test
    fun 삭제된_경험을_가리켜도_산출물은_정상_복원된다() {
        // given — 경험은 별도로 저장하지 않고 식별자만 둔다(소프트 참조, FK 없음 증명)
        val artifact = resume(ownerId)
        val saved = repository.saveAndFlush(artifact)

        // when — experience_records에 어떤 행도 없지만 정상 조회되어야 한다
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)

        // then
        assertThat(found).isNotNull
        val careerSources = found!!.activeVersion().sections
            .first { it.definitionKey == "career" }.sourceExperienceIds
        assertThat(careerSources).isNotEmpty
    }

    @Test
    fun 채택으로_만든_새_버전이_순서대로_복원되고_활성이_전환된다() {
        // given
        val artifact = resume(ownerId)
        val targetId = artifact.activeVersion().sections.first { it.definitionKey == "career" }.id
        artifact.adoptSection(targetId, SectionContent.of("새 경력 내용"), baseTime.plusSeconds(60))

        // when
        val saved = repository.saveAndFlush(artifact)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)!!

        // then
        assertThat(found.versions).hasSize(2)
        val active = found.activeVersion()
        assertThat(active.sections.first { it.definitionKey == "career" }.content.value)
            .isEqualTo("새 경력 내용")
        // 미변경 항목 복제 보존
        assertThat(active.sections.first { it.definitionKey == "summary" }.content.value)
            .isEqualTo("요약 내용")
    }

    @Test
    fun 채택_후_라운드트립에서_직전_버전의_출처_경험이_영향받지_않는다() {
        // given (HIGH-1 — 수용 기준 19, sourceExperienceIds 버전 간 격리 라운드트립 단언)
        val artifact = resume(ownerId)
        val previousVersionId = artifact.activeVersion().id
        val previousCareerSources = artifact.activeVersion()
            .sections.first { it.definitionKey == "career" }.sourceExperienceIds
        val targetId = artifact.activeVersion().sections.first { it.definitionKey == "career" }.id
        artifact.adoptSection(targetId, SectionContent.of("새 경력 내용"), baseTime.plusSeconds(60))

        // when
        val saved = repository.saveAndFlush(artifact)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)!!

        // then — 직전 버전 섹션의 출처 경험이 그대로 보존(다른 버전 섹션이 영향 주지 않음)
        val reloadedPrevious = found.versions.first { it.id == previousVersionId }
        val reloadedPreviousSources =
            reloadedPrevious.sections.first { it.definitionKey == "career" }.sourceExperienceIds
        assertThat(reloadedPreviousSources).containsExactlyElementsOf(previousCareerSources)

        // 새 버전 섹션의 출처도 독립적으로 복원
        val reloadedNew = found.activeVersion()
        val reloadedNewSources =
            reloadedNew.sections.first { it.definitionKey == "career" }.sourceExperienceIds
        assertThat(reloadedNewSources).containsExactlyElementsOf(previousCareerSources)
    }

    @Test
    fun 다른_사용자의_산출물은_조회되지_않는다() {
        // given
        val saved = repository.saveAndFlush(resume(ownerId))

        // when
        val foundByOther = repository.findByIdAndOwnerId(saved.id, otherOwnerId)

        // then
        assertThat(foundByOther).isNull()
    }

    @Test
    fun 타인_소유_산출물을_본인_식별자로_조회해도_null이_반환된다() {
        // given (수용 기준 13) — 타 사용자 산출물을 실제로 영속한 뒤, 그 식별자로 본인이 조회하면 격리되어야 한다.
        val othersArtifact = repository.saveAndFlush(resume(otherOwnerId))

        // when — 존재하는 식별자지만 소유자가 다르므로 격리된다.
        val foundByMe = repository.findByIdAndOwnerId(othersArtifact.id, ownerId)

        // then
        assertThat(foundByMe).isNull()
        // 실제 소유자로는 정상 복원되어 "미존재가 아니라 격리"임을 증명한다.
        assertThat(repository.findByIdAndOwnerId(othersArtifact.id, otherOwnerId)).isNotNull
    }

    @Test
    fun 소유자_기준_목록_조회는_본인_데이터만_가져온다() {
        // given
        repository.saveAndFlush(resume(ownerId))
        repository.saveAndFlush(resume(ownerId))
        repository.saveAndFlush(resume(otherOwnerId))

        // when
        val mine = repository.findAllByOwnerId(ownerId)

        // then
        assertThat(mine).hasSize(2)
    }

    @Test
    fun 소유자_기준_삭제는_본인_데이터만_지운다() {
        // given
        repository.saveAndFlush(resume(ownerId))
        repository.saveAndFlush(resume(otherOwnerId))

        // when
        repository.deleteByOwnerId(ownerId)
        repository.flush()

        // then
        assertThat(repository.findAllByOwnerId(ownerId)).isEmpty()
        assertThat(repository.findAllByOwnerId(otherOwnerId)).hasSize(1)
    }
}
