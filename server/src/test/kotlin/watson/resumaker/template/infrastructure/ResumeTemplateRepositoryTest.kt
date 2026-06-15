package watson.resumaker.template.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import watson.resumaker.account.domain.UserId
import watson.resumaker.template.domain.ResumeTemplate
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.domain.TemplateName
import java.util.UUID

@DataJpaTest
class ResumeTemplateRepositoryTest {

    @Autowired
    private lateinit var repository: ResumeTemplateRepository

    private val ownerId = UserId(UUID.randomUUID())
    private val otherOwnerId = UserId(UUID.randomUUID())

    private fun section(name: String, character: SectionCharacter, required: Boolean = false) =
        SectionDefinition.of(name, character, required)

    @Test
    fun 양식을_저장하고_섹션을_순서대로_그대로_복원한다() {
        // given
        val template = ResumeTemplate.create(
            ownerId = ownerId,
            name = TemplateName("토스 백엔드 지원용"),
            sections = listOf(
                section("한 줄 자기소개", SectionCharacter.SUMMARY, required = true),
                section("핵심 역량", SectionCharacter.SUMMARY),
                section("주요 경력", SectionCharacter.CAREER, required = true),
            ),
        )

        // when
        val saved = repository.saveAndFlush(template)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.name.value).isEqualTo("토스 백엔드 지원용")
        // 순서 보존(@OrderColumn) 검증.
        assertThat(found.sections.map { it.name })
            .containsExactly("한 줄 자기소개", "핵심 역량", "주요 경력")
        assertThat(found.sections.map { it.character })
            .containsExactly(SectionCharacter.SUMMARY, SectionCharacter.SUMMARY, SectionCharacter.CAREER)
        assertThat(found.sections.map { it.required })
            .containsExactly(true, false, true)
    }

    @Test
    fun 다른_사용자의_양식은_조회되지_않는다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("내 양식"), listOf(section("요약", SectionCharacter.SUMMARY)))
        val saved = repository.saveAndFlush(template)

        // when
        val foundByOther = repository.findByIdAndOwnerId(saved.id, otherOwnerId)

        // then
        assertThat(foundByOther).isNull()
    }

    @Test
    fun 소유자_기준_목록_조회는_본인_데이터만_가져온다() {
        // given
        repository.saveAndFlush(ResumeTemplate.create(ownerId, TemplateName("내 양식 1"), listOf(section("요약", SectionCharacter.SUMMARY))))
        repository.saveAndFlush(ResumeTemplate.create(ownerId, TemplateName("내 양식 2"), listOf(section("경력", SectionCharacter.CAREER))))
        repository.saveAndFlush(ResumeTemplate.create(otherOwnerId, TemplateName("남의 양식"), listOf(section("요약", SectionCharacter.SUMMARY))))

        // when
        val mine = repository.findAllByOwnerId(ownerId)

        // then
        assertThat(mine).hasSize(2)
        assertThat(mine.map { it.name.value }).containsExactlyInAnyOrder("내 양식 1", "내 양식 2")
    }

    @Test
    fun update_후_재조회하면_새_섹션_집합이_복원되고_고아_행이_없다() {
        // given — 섹션 3개로 저장
        val template = ResumeTemplate.create(
            ownerId = ownerId,
            name = TemplateName("초기 양식"),
            sections = listOf(
                section("한 줄 자기소개", SectionCharacter.SUMMARY, required = true),
                section("핵심 역량", SectionCharacter.SUMMARY),
                section("주요 경력", SectionCharacter.CAREER, required = true),
            ),
        )
        val saved = repository.saveAndFlush(template)

        // when — 섹션 2개로 축소 + 순서 재배열(경력형을 첫 번째로)
        val newSections = listOf(
            section("주요 경력", SectionCharacter.CAREER, required = true),
            section("핵심 역량", SectionCharacter.SUMMARY),
        )
        saved.update(TemplateName("수정된 양식"), newSections)
        repository.saveAndFlush(saved)

        // then — 재조회 시 새 집합만 존재해야 한다(@ElementCollection delete-all+재삽입 동작 증명)
        val found = repository.findByIdAndOwnerId(saved.id, ownerId)!!
        assertThat(found.name.value).isEqualTo("수정된 양식")
        assertThat(found.sections).hasSize(2)
        assertThat(found.sections.map { it.name }).containsExactly("주요 경력", "핵심 역량")
        assertThat(found.sections.map { it.character })
            .containsExactly(SectionCharacter.CAREER, SectionCharacter.SUMMARY)
        assertThat(found.sections[0].required).isTrue()
        assertThat(found.sections[1].required).isFalse()
    }

    @Test
    fun 소유자_기준_삭제는_본인_데이터만_지운다() {
        // given
        repository.saveAndFlush(ResumeTemplate.create(ownerId, TemplateName("내 양식"), listOf(section("요약", SectionCharacter.SUMMARY))))
        repository.saveAndFlush(ResumeTemplate.create(otherOwnerId, TemplateName("남의 양식"), listOf(section("요약", SectionCharacter.SUMMARY))))

        // when
        repository.deleteByOwnerId(ownerId)
        repository.flush()

        // then
        assertThat(repository.findAllByOwnerId(ownerId)).isEmpty()
        assertThat(repository.findAllByOwnerId(otherOwnerId)).hasSize(1)
    }
}
