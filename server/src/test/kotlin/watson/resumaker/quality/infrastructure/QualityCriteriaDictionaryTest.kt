package watson.resumaker.quality.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [QualityCriteriaDictionary] 단위 테스트(AI-06 — 어절 시작 경계 매칭). 기본 시드 사전을 쓴다.
 *
 * 검증: 전체-단어 사전(약한 동사·버즈워드·규모어)은 부분 문자열 거짓 양성을 더는 만들지 않되 진짜 어절은 잡고,
 * 수동 종결 접미는 어간 뒤(부분 문자열)에서도 그대로 잡힌다.
 */
class QualityCriteriaDictionaryTest {

    private val dictionary = QualityCriteriaDictionary(QualityCriteriaProperties())

    @Test
    fun 버즈워드_소통은_의사소통의_부분문자열로는_매칭되지_않는다() {
        // given/then — "소통"이 "의사소통"의 일부라서 잘못 잡히던 거짓 양성이 사라진다.
        assertThat(dictionary.findBuzzword("뛰어난 의사소통 능력을 갖췄어요.")).isNull()
    }

    @Test
    fun 버즈워드_소통은_어절_시작에서는_그대로_매칭된다() {
        // given/then — 진짜 버즈워드 어절은 여전히 잡는다(부분 매칭 제거가 진짜 사례를 놓치지 않는다).
        assertThat(dictionary.findBuzzword("소통 능력이 뛰어나요.")).isEqualTo("소통")
        assertThat(dictionary.findBuzzword("소통능력이 뛰어나요.")).isEqualTo("소통")
    }

    @Test
    fun 약한동사_있었다는_재미있었다의_부분문자열로는_매칭되지_않는다() {
        // given/then — "있었다"가 "재미있었다"의 일부라서 잘못 잡히던 거짓 양성이 사라진다.
        assertThat(dictionary.findWeakVerb("그 일은 정말 재미있었다.")).isNull()
    }

    @Test
    fun 약한동사_있었다는_어절_시작에서는_그대로_매칭된다() {
        assertThat(dictionary.findWeakVerb("뚜렷한 경쟁력이 있었다.")).isEqualTo("있었다")
    }

    @Test
    fun 모호수치_퍼센트_변화는_정규식으로_검출된다() {
        // given/then (AI-01·AP5) — 기준선 없는 "200% 증가"·"30% 개선"을 시드 사전이 아닌 정규식이 잡는다.
        assertThat(dictionary.findVagueMetric("매출을 200% 증가시켰어요.")).isEqualTo("200% 증가")
        assertThat(dictionary.findVagueMetric("응답 속도를 30% 개선했어요.")).isEqualTo("30% 개선")
    }

    @Test
    fun 모호수치_배수와_규모수사도_검출된다() {
        // given/then (AI-01·AP5) — "N배", "수십·수백" 류.
        assertThat(dictionary.findVagueMetric("처리량이 10배 늘었어요.")).isEqualTo("10배")
        assertThat(dictionary.findVagueMetric("수십 개의 기능을 만들었어요.")).isEqualTo("수십")
    }

    @Test
    fun 시드_규모어와_정규식은_합집합으로_본다() {
        // given/then — 시드 규모어("대용량")는 여전히 잡고, 둘 다 없으면 null.
        assertThat(dictionary.findVagueMetric("대용량 트래픽을 처리했어요.")).isEqualTo("대용량")
        assertThat(dictionary.findVagueMetric("응답 속도를 40% 단축해 320ms를 달성했어요.")).isEqualTo("40% 단축")
        assertThat(dictionary.findVagueMetric("결제 모듈을 구현했어요.")).isNull()
    }

    @Test
    fun 수동태_종결은_어간_뒤_접미에서도_매칭된다() {
        // given/then — 수동 종결("되어")은 어간("구현")에 붙는 접미라 경계 매칭을 쓰지 않고 그대로 잡는다.
        assertThat(dictionary.findPassiveVoice("결제 모듈이 구현되어 운영됐어요.")).isEqualTo("되어")
    }

    @Test
    fun 같은_끝맺음이_세_문장_이상_반복되면_단조로_검출된다() {
        // given/then (AI-09·K1) — "했다"가 세 문장에서 반복.
        assertThat(dictionary.findMonotonousEnding("결제를 설계했다. 서버를 구현했다. 배포를 자동화했다."))
            .isEqualTo("했다")
    }

    @Test
    fun 끝맺음이_다양하면_단조로_보지_않는다() {
        // given/then (K1) — 끝맺음이 제각각이면 null.
        assertThat(dictionary.findMonotonousEnding("결제를 설계했어요. 서버를 구현했고 안정화했습니다. 배포를 자동화함."))
            .isNull()
    }

    @Test
    fun 같은_라틴_용어의_표기_변형을_검출한다() {
        // given/then (AI-09·K2) — "API"와 "Api"는 같은 용어의 다른 표기.
        val variant = dictionary.findNotationVariant("API를 설계하고 Api 문서를 작성했어요.")
        assertThat(variant).isNotNull
        assertThat(variant!!.lowercase()).contains("api")
        // 표기가 일관되면 null.
        assertThat(dictionary.findNotationVariant("API를 일관되게 설계했어요.")).isNull()
    }
}
