package watson.resumaker.target.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.resumaker.common.domain.DomainValidationException

/**
 * 채용 방향 불변식의 단일 소유자. 목표(TargetBrief)와 산출물 목표 스냅샷(ArtifactTargetSnapshot)이
 * 모두 이 VO로 검증을 위임하므로, 길이·공백 규칙은 여기서만 검증한다.
 */
class RecruitDirectionTest {

    @Test
    fun `공백이면 거부한다`() {
        assertThatThrownBy { RecruitDirection("   ") }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun `채용공고 전문 수준의 긴 채용 방향(200자 초과)도 허용한다`() {
        // QA 2026-06-21 #1·#2: 채용공고를 그대로 붙여넣어도 수용해야 한다.
        val long = "가".repeat(2000)

        assertThat(RecruitDirection(long).value).hasSize(2000)
    }

    @Test
    fun `상한(MAX_LENGTH)까지 허용하고 초과하면 거부한다`() {
        val atLimit = "가".repeat(RecruitDirection.MAX_LENGTH)

        assertThat(RecruitDirection(atLimit).value).hasSize(RecruitDirection.MAX_LENGTH)
        assertThatThrownBy { RecruitDirection("가".repeat(RecruitDirection.MAX_LENGTH + 1)) }
            .isInstanceOf(DomainValidationException::class.java)
    }
}
