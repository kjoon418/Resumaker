package watson.resumaker.target.domain

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import watson.resumaker.common.domain.DomainValidationException

class TargetVoTest {

    @Nested
    inner class 채용_방향_검증 {

        @Test
        fun 채용_방향이_비어_있으면_예외를_던진다() {
            // given
            val blankDirection = " "

            // when and then
            assertThatThrownBy { RecruitDirection(blankDirection) }
                .isInstanceOf(DomainValidationException::class.java)
        }

        @Test
        fun 적절한_채용_방향이면_생성에_성공한다() {
            // when and then
            assertThatCode { RecruitDirection("백엔드 개발자, 대용량 트래픽 경험 우대") }
                .doesNotThrowAnyException()
        }
    }

    @Nested
    inner class 회사명_검증 {

        @Test
        fun 회사명이_비어_있으면_예외를_던진다() {
            // given
            val blankCompany = ""

            // when and then
            assertThatThrownBy { CompanyName(blankCompany) }
                .isInstanceOf(DomainValidationException::class.java)
        }
    }

    @Nested
    inner class 직무명_검증 {

        @Test
        fun 직무명이_비어_있으면_예외를_던진다() {
            // given
            val blankJob = ""

            // when and then
            assertThatThrownBy { JobTitle(blankJob) }
                .isInstanceOf(DomainValidationException::class.java)
        }
    }
}
