package watson.resumaker.quality.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.generation.application.FactTokenExtractor

/**
 * [FactTokenPreservationValidator] 단위 테스트(QC4 — 개선 특유 불변식). 순수·결정적.
 *
 * 검증: 원본의 수치·고유명사 토큰이 후보에 모두 보존되면 통과, 하나라도 누락·변형되면 실패.
 */
class FactTokenPreservationValidatorTest {

    private val validator = FactTokenPreservationValidator(FactTokenExtractor())

    @Test
    fun 사실_토큰을_모두_보존한_다듬기는_통과한다() {
        // given — 표현만 다듬고 500·Kotlin은 그대로.
        val preserved = validator.preserves(
            original = "Kotlin으로 초당 500건을 담당했다.",
            candidate = "Kotlin으로 초당 500건을 안정적으로 처리했어요.",
        )

        // then
        assertThat(preserved).isTrue()
    }

    @Test
    fun 원본_수치를_흘리면_실패한다() {
        // given — 후보가 500을 빠뜨렸다.
        val preserved = validator.preserves(
            original = "초당 500건을 처리했다.",
            candidate = "많은 요청을 빠르게 처리했어요.",
        )

        // then
        assertThat(preserved).isFalse()
        assertThat(validator.missingTokens("초당 500건을 처리했다.", "많은 요청을 처리했어요.")).contains("500")
    }

    @Test
    fun 원본_고유명사를_변형하면_실패한다() {
        // given — Kotlin을 Java로 바꿨다(고유명사 변형 = 누락).
        val preserved = validator.preserves(
            original = "Kotlin으로 서버를 만들었다.",
            candidate = "Java로 서버를 만들었어요.",
        )

        // then — 원본 토큰 kotlin이 후보에 없어 실패.
        assertThat(preserved).isFalse()
    }

    @Test
    fun 후보가_근거_있는_표현을_더해도_원본_보존이면_통과한다() {
        // given — 보존 검증은 "원본 토큰이 후보에 모두 있는가"만 본다(새 토큰 추가 0건은 신뢰성 검증의 책임).
        val preserved = validator.preserves(
            original = "500건을 처리했다.",
            candidate = "Kotlin으로 500건을 처리했어요.",
        )

        // then — 원본 토큰(500)이 보존됐으므로 보존 검증은 통과(Kotlin 추가의 정당성은 신뢰성 검증이 본다).
        assertThat(preserved).isTrue()
    }
}
