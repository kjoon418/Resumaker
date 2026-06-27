package watson.resumaker.quality.infrastructure

import org.springframework.stereotype.Component
import watson.resumaker.quality.domain.QualityCriterion

/**
 * 개선 기준 **결정적 검사기 모음**(품질 개선 기획 §1·§2·§4.1). 시드 사전·임계값([QualityCriteriaProperties])을 받아
 * 산출물 텍스트에서 위반/약점을 결정적으로 찾아낸다. 순수·결정적이며 외부 호출이 없다(같은 입력 → 같은 결과, QC1).
 *
 * 각 검사는 한 항목 텍스트(또는 항목 쌍)에서 위반의 **근거 문자열**을 돌려준다(없으면 null/빈 목록). 서비스
 * ([watson.resumaker.quality.application.QualityReviewService])가 이 근거로 [watson.resumaker.quality.domain.Finding]을
 * 만들고 처치 종류를 분기한다.
 *
 * 한국어 형태소 분석 없이 **사전 매칭·패턴·길이·문자 n-그램 유사도**만 쓴다(결정성 우선, 오너 큐레이션으로 사전 교체).
 */
@Component
class QualityCriteriaDictionary(
    private val properties: QualityCriteriaProperties,
) {

    /** 모호 수치 정규식(AI-01·AP5)을 1회만 컴파일해 둔다. */
    private val vagueMetricRegexes: List<Regex> = properties.vagueMetricPatterns.map { Regex(it) }

    /** I1 약한 동사(STRONG_VERB): 어절 시작 경계에서 매칭된 첫 약한 동사를 근거로 돌려준다(없으면 null). */
    fun findWeakVerb(content: String): String? =
        properties.weakVerbs.firstOrNull { containsAtWordStart(content, it) }

    /** C2 버즈워드(BUZZWORD): 어절 시작 경계에서 매칭된 첫 버즈워드를 근거로 돌려준다. */
    fun findBuzzword(content: String): String? =
        properties.buzzwords.firstOrNull { containsAtWordStart(content, it) }

    /**
     * I4 모호 수치·규모어(VAGUE_METRIC, AI-01): 시드 규모어 사전과 모호 수치 정규식(AP5)의 **합집합**으로 본다.
     * 어절 시작 경계에서 매칭된 첫 규모어를 근거로, 없으면 정규식이 잡은 첫 모호 수치 표현(매칭 문자열)을 근거로 돌려준다.
     * 둘 다 없으면 null.
     */
    fun findVagueMetric(content: String): String? =
        properties.vagueMetricTerms.firstOrNull { containsAtWordStart(content, it) }
            ?: vagueMetricRegexes.firstNotNullOfOrNull { it.find(content)?.value }

    /**
     * I2 수동태(ACTIVE_VOICE): 매칭된 첫 수동 종결 패턴을 근거로 돌려준다.
     * 수동 종결("되었다"·"되어")은 **어간에 붙는 접미사**라 어절 시작 경계 매칭을 쓰지 않는다(예: "구현되어"의
     * "되어"는 앞 글자가 어간이므로 경계 매칭이면 못 잡는다). 사전 항목 중 유일하게 contains를 유지한다.
     */
    fun findPassiveVoice(content: String): String? =
        properties.passiveSuffixes.firstOrNull { content.contains(it) }

    /** C1 분량 적정(LENGTH): 길이 상한을 넘으면 true(자동·결정적). */
    fun exceedsLength(content: String): Boolean =
        content.trim().length > properties.maxSectionLength

    /** St2 빈 항목(EMPTY_SECTION): 공백만 있으면 true. */
    fun isEmptyContent(content: String): Boolean = content.isBlank()

    /**
     * C3 중복(DUPLICATION): 두 항목 텍스트의 문자 n-그램(shingle) 자카드 유사도가 임계 이상이면 겹친다고 본다.
     * 공백·개행을 제거해 표기 흔들림에 둔감하게 만든다. 짧은 텍스트(샤이클 미만)는 유사도 0으로 본다(오탐 방지).
     */
    fun isDuplicate(a: String, b: String): Boolean =
        jaccardSimilarity(a, b) >= properties.duplicationThreshold

    private fun jaccardSimilarity(a: String, b: String): Double {
        val sa = shingles(a)
        val sb = shingles(b)
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        val intersection = sa.intersect(sb).size.toDouble()
        val union = sa.union(sb).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun shingles(text: String): Set<String> {
        val normalized = text.replace(WHITESPACE_REGEX, "")
        val size = properties.duplicationShingleSize
        if (normalized.length < size) return emptySet()
        return (0..normalized.length - size).map { normalized.substring(it, it + size) }.toSet()
    }

    /**
     * AI-06: 사전 항목이 **어절 시작 경계**에서 등장할 때만 매칭한다(바로 앞 글자가 글자/숫자가 아닐 때).
     * `contains` 부분 문자열 매칭이 "소통"을 "의사소통"에서, "있었다"를 "재미있었다"에서 잘못 잡던 거짓 양성을
     * 제거한다. 어절 시작에서만 보므로 "소통 능력"·"소통능력"·"담당했다"는 그대로 매칭된다. 유료 처치(AUTO_REWRITE)를
     * 유발하는 전체-단어 사전(약한 동사·버즈워드·규모어)에 적용한다(수동태 접미는 제외 — 위 주석 참고).
     *
     * **알려진 잔여 한계:** 어절 시작에 진짜로 등장하는 단어의 의미적 거짓 양성(예: "복잡한 레거시를 모듈화"의
     * "복잡한")은 형태소·문맥 분석 없이 결정적으로 가릴 수 없어 남는다(오너 사전 큐레이션 대상).
     */
    private fun containsAtWordStart(content: String, term: String): Boolean {
        if (term.isEmpty()) return false
        var idx = content.indexOf(term)
        while (idx >= 0) {
            val before = content.getOrNull(idx - 1)
            if (before == null || !before.isLetterOrDigit()) return true
            idx = content.indexOf(term, idx + 1)
        }
        return false
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
