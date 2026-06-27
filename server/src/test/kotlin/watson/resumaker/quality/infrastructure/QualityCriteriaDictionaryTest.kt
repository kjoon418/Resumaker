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
    fun 수동태_종결은_어간_뒤_접미에서도_매칭된다() {
        // given/then — 수동 종결("되어")은 어간("구현")에 붙는 접미라 경계 매칭을 쓰지 않고 그대로 잡는다.
        assertThat(dictionary.findPassiveVoice("결제 모듈이 구현되어 운영됐어요.")).isEqualTo("되어")
    }
}
