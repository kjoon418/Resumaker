package watson.resumaker.template.domain

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.resumaker.common.domain.DomainValidationException

class TemplateVoTest {

    @Nested
    inner class 양식_이름_검증 {

        @Test
        fun 이름이_비어_있으면_예외를_던진다() {
            // given
            val blankName = " "

            // when and then
            assertThatThrownBy { TemplateName(blankName) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 적절한_이름이면_생성에_성공한다() {
            // when and then
            assertThatCode { TemplateName("토스 백엔드 지원용") }
                .doesNotThrowAnyException()
        }
    }

    @Nested
    inner class 섹션_정의_검증 {

        @Test
        fun 섹션_이름이_비어_있으면_예외를_던진다() {
            // given
            val blankName = ""

            // when and then
            assertThatThrownBy { SectionDefinition.of(blankName, SectionCharacter.SUMMARY, false) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 이름_성격_필수여부를_담아_생성에_성공한다() {
            // when and then
            assertThatCode { SectionDefinition.of("핵심 역량", SectionCharacter.SUMMARY, required = true) }
                .doesNotThrowAnyException()
        }
    }
}
