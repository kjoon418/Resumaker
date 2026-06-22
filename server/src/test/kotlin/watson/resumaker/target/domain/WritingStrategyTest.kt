package watson.resumaker.target.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [WritingStrategy] JSON 직렬화/역직렬화 라운드트립. 엔티티는 이 VO를 JSON 문자열로만 보관하므로(application 계층이
 * Jackson으로 직렬화), 5필드가 손실 없이 왕복하는지 고정한다.
 */
class WritingStrategyTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun 작성_전략은_JSON으로_왕복해도_5필드가_보존된다() {
        // given
        val strategy = WritingStrategy(
            keywords = listOf("대용량 트래픽", "Kotlin"),
            tone = "담백하고 성과 중심",
            emphasize = listOf("백엔드 설계 경험"),
            avoid = listOf("과장된 표현"),
            summary = "백엔드 신입 — 대용량 처리 역량 강조",
        )

        // when
        val json = objectMapper.writeValueAsString(strategy)
        val restored = objectMapper.readValue<WritingStrategy>(json)

        // then
        assertThat(restored).isEqualTo(strategy)
    }

    @Test
    fun 빈_목록_필드도_그대로_왕복한다() {
        // given — keywords/emphasize/avoid가 빈 목록이어도 손실 없이 복원된다.
        val strategy = WritingStrategy(
            keywords = emptyList(),
            tone = "",
            emphasize = emptyList(),
            avoid = emptyList(),
            summary = "요약만 있음",
        )

        // when
        val restored = objectMapper.readValue<WritingStrategy>(objectMapper.writeValueAsString(strategy))

        // then
        assertThat(restored).isEqualTo(strategy)
    }
}
