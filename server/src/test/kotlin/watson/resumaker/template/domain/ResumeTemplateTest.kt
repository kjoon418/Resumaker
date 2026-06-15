package watson.resumaker.template.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.DomainValidationException
import java.util.UUID

class ResumeTemplateTest {

    private val ownerId = UserId(UUID.randomUUID())

    private fun section(name: String, character: SectionCharacter = SectionCharacter.SUMMARY) =
        SectionDefinition.of(name, character, required = false)

    @Test
    fun 신규_생성_시_식별자를_발급하고_소유자와_섹션_순서를_가진다() {
        // when
        val template = ResumeTemplate.create(
            ownerId = ownerId,
            name = TemplateName("기본 이력서"),
            sections = listOf(
                section("한 줄 자기소개"),
                section("주요 경력", SectionCharacter.CAREER),
            ),
        )

        // then
        assertThat(template.id.value).isNotNull()
        assertThat(template.ownerId).isEqualTo(ownerId)
        assertThat(template.sections.map { it.name }).containsExactly("한 줄 자기소개", "주요 경력")
    }

    @Test
    fun 섹션이_하나도_없으면_생성이_거부된다() {
        // when and then
        assertThatThrownBy {
            ResumeTemplate.create(ownerId, TemplateName("빈 양식"), emptyList())
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun 섹션이_하나라도_있으면_생성에_성공한다() {
        // when and then
        assertThatCode {
            ResumeTemplate.create(ownerId, TemplateName("최소 양식"), listOf(section("요약")))
        }.doesNotThrowAnyException()
    }

    @Test
    fun 수정하면_이름과_섹션이_갱신된다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("기존"), listOf(section("기존 섹션")))

        // when
        template.update(
            name = TemplateName("새 이름"),
            sections = listOf(section("핵심 역량"), section("주요 경력", SectionCharacter.CAREER)),
        )

        // then
        assertThat(template.name.value).isEqualTo("새 이름")
        assertThat(template.sections.map { it.name }).containsExactly("핵심 역량", "주요 경력")
    }

    @Test
    fun 빈_섹션_목록으로_수정하면_거부된다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("기존"), listOf(section("기존 섹션")))

        // when and then
        assertThatThrownBy {
            template.update(TemplateName("새 이름"), emptyList())
        }.isInstanceOf(DomainValidationException::class.java)
    }
}
