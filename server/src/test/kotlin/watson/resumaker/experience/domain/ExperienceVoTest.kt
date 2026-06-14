package watson.resumaker.experience.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.resumaker.common.domain.DomainValidationException
import java.time.LocalDate

class ExperienceVoTest {

    @Nested
    inner class 경험_제목_검증 {

        @Test
        fun 제목이_비어_있으면_예외를_던진다() {
            // given
            val blankTitle = " "

            // when and then
            assertThatThrownBy { ExperienceTitle(blankTitle) }
                .isInstanceOf(DomainValidationException::class.java)
                .hasMessage("경험의 제목을 입력해 주세요.")
        }

        @Test
        fun 제목이_최대_길이를_초과하면_예외를_던진다() {
            // given
            val tooLongTitle = "가".repeat(ExperienceTitle.MAX_LENGTH + 1)

            // when and then
            assertThatThrownBy { ExperienceTitle(tooLongTitle) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 적절한_제목이면_생성에_성공한다() {
            // when and then
            assertThatCode { ExperienceTitle("결제 시스템 개편 프로젝트") }
                .doesNotThrowAnyException()
        }
    }

    @Nested
    inner class 경험_본문_검증 {

        @Test
        fun 본문이_비어_있으면_예외를_던진다() {
            // given
            val blankBody = ""

            // when and then
            assertThatThrownBy { ExperienceBody(blankBody) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 적절한_본문이면_생성에_성공한다() {
            // when and then
            assertThatCode { ExperienceBody("캐싱 전략을 도입해 응답 속도를 개선했다.") }
                .doesNotThrowAnyException()
        }
    }

    @Nested
    inner class 역량_태그_검증 {

        @Test
        fun 태그가_비어_있으면_예외를_던진다() {
            // given
            val blankTag = " "

            // when and then
            assertThatThrownBy { SkillTag(blankTag) }
                .isInstanceOf(DomainValidationException::class.java)
        }
    }

    @Nested
    inner class 기간_검증 {

        @Test
        fun 시작일이_종료일보다_늦으면_예외를_던진다() {
            // given
            val start = LocalDate.of(2024, 5, 1)
            val end = LocalDate.of(2024, 1, 1)

            // when and then
            assertThatThrownBy { Period.of(start, end) }
                .isInstanceOf(DomainValidationException::class.java)
                .hasMessage("기간의 시작일이 종료일보다 늦을 수 없어요.")
        }

        @Test
        fun 시작일과_종료일이_같으면_생성에_성공한다() {
            // given
            val sameDay = LocalDate.of(2024, 1, 1)

            // when and then
            assertThatCode { Period.of(sameDay, sameDay) }
                .doesNotThrowAnyException()
        }

        @Test
        fun 시작일이_종료일보다_빠르면_생성에_성공한다() {
            // when
            val period = Period.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 5, 1))

            // then
            assertThat(period.start).isBefore(period.end)
        }
    }
}
