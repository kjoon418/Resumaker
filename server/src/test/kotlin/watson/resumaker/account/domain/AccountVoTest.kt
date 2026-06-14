package watson.resumaker.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.resumaker.common.domain.DomainValidationException

class AccountVoTest {

    @Nested
    inner class 인증_수단_검증 {

        @Test
        fun 이메일_형식이_올바르지_않으면_예외를_던진다() {
            // given
            val invalidEmail = "not-an-email"

            // when and then
            assertThatThrownBy { Credential.of(invalidEmail, "hashed") }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 비밀번호_해시가_비어_있으면_예외를_던진다() {
            // given
            val blankHash = ""

            // when and then
            assertThatThrownBy { Credential.of("user@example.com", blankHash) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 적절한_이메일과_해시면_생성에_성공한다() {
            // when and then
            assertThatCode { Credential.of("user@example.com", "hashed") }
                .doesNotThrowAnyException()
        }
    }

    @Nested
    inner class 시간대_검증 {

        @Test
        fun 유효하지_않은_시간대면_예외를_던진다() {
            // given
            val invalidZone = "Not/AZone"

            // when and then
            assertThatThrownBy { UserTimeZone(invalidZone) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 유효한_시간대면_생성에_성공한다() {
            // when
            val timeZone = UserTimeZone("Asia/Seoul")

            // then
            assertThat(timeZone.toZoneId().id).isEqualTo("Asia/Seoul")
        }
    }

    @Nested
    inner class 사용자_생성 {

        @Test
        fun 신규_생성_시_식별자를_발급한다() {
            // when
            val user = User.create(
                credential = Credential.of("user@example.com", "hashed"),
                timeZone = UserTimeZone.DEFAULT,
            )

            // then
            assertThat(user.id.value).isNotNull()
        }
    }
}
