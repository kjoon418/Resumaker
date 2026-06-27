package watson.resumaker.quality.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.FactTokenExtractor
import java.util.UUID

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
    fun 파생_수치를_축약한_압축_후보는_통과한다() {
        // given (AI-02) — 길이/압축 처치가 군더더기 파생 수치(500)를 덜어내고 표현만 다듬었다. 새 수치를 만들지
        //                 않았으므로(후보 ⊆ 원본) 보존 검증은 통과해야 한다(수치는 "변형·날조 금지"로 좁힘).
        val preserved = validator.preserves(
            original = "초당 500건을 처리했다.",
            candidate = "많은 요청을 빠르게 처리했어요.",
        )

        // then — 파생 수치 제거는 위반이 아니다.
        assertThat(preserved).isTrue()
        assertThat(validator.missingTokens("초당 500건을 처리했다.", "많은 요청을 처리했어요.")).isEmpty()
    }

    @Test
    fun 원본에_없던_새_수치를_더한_후보는_실패한다() {
        // given (AI-02) — 후보가 원본에 없던 수치(99)를 새로 만들어 넣었다(날조). 보존 검증이 막아야 한다.
        val preserved = validator.preserves(
            original = "요청을 처리했다.",
            candidate = "요청 99건을 처리했어요.",
        )

        // then — 새 수치는 위반.
        assertThat(preserved).isFalse()
        assertThat(validator.missingTokens("요청을 처리했다.", "요청 99건을 처리했어요.")).contains("99")
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

    // ── M2: skillTags 사전으로 한글 고유명사 보존 ──────────────────────────────────

    @Test
    fun 원본의_한글_skillTag를_후보가_누락하면_실패한다() {
        // given — "스프링부트"는 따옴표 없는 순수 한글이라 사실 토큰 추출로는 안 잡히지만, 사용자가 skillTag로
        //         선언했으므로 보존 사전에 든다. 후보가 일반어("백엔드 프레임워크")로 바꿔 누락하면 실패해야 한다.
        val experiences = listOf(experience(skillTags = listOf("스프링부트")))
        val preserved = validator.preserves(
            original = "스프링부트로 결제 서버를 만들었다.",
            candidate = "한 백엔드 프레임워크로 결제 서버를 만들었어요.",
            experiences = experiences,
        )

        // then
        assertThat(preserved).isFalse()
        assertThat(
            validator.missingTokens(
                "스프링부트로 결제 서버를 만들었다.",
                "한 백엔드 프레임워크로 결제 서버를 만들었어요.",
                experiences,
            ),
        ).contains("스프링부트")
    }

    @Test
    fun 원본의_한글_skillTag를_후보가_유지하면_통과한다() {
        // given — 같은 skillTag를 그대로 남기고 표현만 다듬은 경우.
        val preserved = validator.preserves(
            original = "스프링부트로 결제 서버를 만들었다.",
            candidate = "스프링부트로 결제 서버를 직접 구축했어요.",
            experiences = listOf(experience(skillTags = listOf("스프링부트"))),
        )

        // then
        assertThat(preserved).isTrue()
    }

    @Test
    fun 원본에_없던_skillTag는_보존_대상이_아니다() {
        // given — skillTag "카프카"는 원본 항목에 등장하지 않았다(다른 경험의 태그). 후보에 없어도 보존 위반이 아니다.
        val preserved = validator.preserves(
            original = "스프링부트로 결제 서버를 만들었다.",
            candidate = "스프링부트로 결제 서버를 안정적으로 운영했어요.",
            experiences = listOf(experience(skillTags = listOf("스프링부트", "카프카"))),
        )

        // then — 원본에 있던 스프링부트만 보존하면 되고, 카프카는 무관.
        assertThat(preserved).isTrue()
    }

    @Test
    fun experiences가_비면_사실_토큰만_본다_기존_동작_유지() {
        // given — skillTag 사전 없이도(빈 experiences) 기존 사실 토큰 보존 동작은 그대로다.
        val preserved = validator.preserves(
            original = "스프링부트로 만들었다.",
            candidate = "한 프레임워크로 만들었어요.",
        )

        // then — 순수 한글 "스프링부트"는 사실 토큰으로 안 잡히고 skillTag 사전도 없어, 보존 검증이 못 잡는다(M2 기존 한계).
        assertThat(preserved).isTrue()
    }

    private fun experience(skillTags: List<String>) = ExperienceSnapshot(
        id = ExperienceRecordId(UUID.randomUUID()),
        title = "결제 서버 구축",
        body = "스프링부트로 결제 서버를 만들었다.",
        situation = null,
        action = null,
        result = null,
        skillTags = skillTags,
    )
}
