package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.artifact.domain.FactKind

/**
 * [FactTokenExtractor] 단위 테스트. 1차 생성 신뢰성 검증과 품질 개선 보존 검증(QC4)이 공유하는 추출·정규화·경계
 * 매칭 규칙을 고정한다(추출 일원화 회귀 방지). 순수·결정적이라 외부 더블이 필요 없다.
 */
class FactTokenExtractorTest {

    private val extractor = FactTokenExtractor()

    @Test
    fun 수치_토큰을_추출하고_표기_차이를_같은_값으로_정규화한다() {
        // given (콤마·퍼센트 표기) — 12,000 ≡ 12000, 40% ≡ 40.
        val tokens = extractor.extract("12,000건을 40% 줄였다.")

        // then — NUMERIC 토큰 2개, 정규화 값이 12000·40.
        val numerics = tokens.filter { it.kind == FactKind.NUMERIC }
        assertThat(numerics.map { it.normalized }).contains("12000", "40")
    }

    @Test
    fun 수치_값_집합은_표기_차이를_동치로_본다() {
        // given — 40% 와 40 퍼센트는 같은 값 40.
        val a = extractor.extractNumericValues("성능을 40% 개선")
        val b = extractor.extractNumericValues("성능을 40 퍼센트 개선")

        // then
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun 단위_인지_수치는_값과_정규화_단위를_함께_추출한다() {
        // given (AI-07) — %·ms·명은 흔한 단위로 정규화, "개"는 흔한 단위 밖이라 단위 null.
        val facts = extractor.extractNumericFacts("응답을 40% 줄이고 30ms 단축, 팀원 5명, 기능 3개")

        // then (BigDecimal 스케일 함정을 피해 값 문자열·단위 쌍으로 단언)
        assertThat(facts.map { it.value.toPlainString() to it.unit }).contains(
            "40" to "%",
            "30" to "ms",
            "5" to "명",
            "3" to null,
        )
    }

    @Test
    fun 단위_동의어와_공백은_같은_단위로_정규화된다() {
        // given — "40 퍼센트"(공백·동의어)는 "%"로 정규화.
        val facts = extractor.extractNumericFacts("성능을 40 퍼센트 개선")

        // then
        assertThat(facts.map { it.value.toPlainString() to it.unit }).containsExactly("40" to "%")
    }

    @Test
    fun 라틴_기술명은_고유명사_후보로_추출된다() {
        // given
        val tokens = extractor.extract("Kotlin과 Spring Boot로 개발했다.")

        // then — 각 라틴 단어가 독립 후보(Kotlin·Spring·Boot).
        val nouns = tokens.filter { it.kind == FactKind.PROPER_NOUN }.map { it.text }
        assertThat(nouns).contains("Kotlin", "Spring", "Boot")
    }

    @Test
    fun 순수_한글은_고유명사로_추출하지_않는다_한계() {
        // given — 따옴표 없는 한글은 일반 명사와 구분 불가라 제외(의도된 한계).
        val tokens = extractor.extract("토스에서 결제를 만들었다.")

        // then
        assertThat(tokens.filter { it.kind == FactKind.PROPER_NOUN }).isEmpty()
    }

    @Test
    fun 영문_인접_한글_토큰은_고유명사_후보로_추출된다() {
        // given (AI-08) — "토스"가 라틴 토큰("Pay")과 인접해 등장.
        val candidates = extractor.koreanProperNounCandidates(listOf("토스 Pay 팀에서 결제를 만들었다."))

        // then
        assertThat(candidates).contains("토스")
    }

    @Test
    fun 반복_등장_한글_토큰은_고유명사_후보로_추출된다() {
        // given (AI-08) — "카프카"가 코퍼스에 2회 등장.
        val candidates = extractor.koreanProperNounCandidates(
            listOf("카프카 도입", "카프카 컨슈머를 운영했다."),
        )

        // then
        assertThat(candidates).contains("카프카")
    }

    @Test
    fun 한_번만_등장하고_영문_인접도_아닌_한글_토큰은_후보가_아니다() {
        // given (AI-08) — "결제"가 한 번만, 영문 인접도 아님.
        val candidates = extractor.koreanProperNounCandidates(listOf("결제 시스템을 만들었다."))

        // then — 보수적 휴리스틱이라 단발 일반 명사는 후보로 잡지 않는다.
        assertThat(candidates).doesNotContain("결제")
    }

    @Test
    fun 라틴_단어_경계_매칭은_부분문자열_통과를_막는다() {
        // given — "go"는 "google"·"cargo"의 부분문자열이지만 경계가 다르다.
        val corpus = extractor.normalizeForNoun("Google Cloud와 cargo 빌드")

        // then — 경계 일치 실패(부분문자열 통과 차단).
        assertThat(extractor.containsLatinWord(corpus, "go")).isFalse()
        // 독립 단어는 경계 일치.
        assertThat(extractor.containsLatinWord(extractor.normalizeForNoun("Go 언어"), "go")).isTrue()
    }

    @Test
    fun 따옴표_인용구는_경계_포함_부분문자열로_대조한다() {
        // given — 한글 포함 인용 프로젝트명.
        val corpus = extractor.normalizeForNoun("사내 결제 플랫폼 구축을 담당했다.")

        // then
        assertThat(extractor.containsQuotedPhrase(corpus, extractor.normalizeForNoun("사내 결제 플랫폼"))).isTrue()
        assertThat(extractor.containsQuotedPhrase(corpus, extractor.normalizeForNoun("가상 결제 엔진"))).isFalse()
    }
}
