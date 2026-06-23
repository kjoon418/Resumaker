package watson.resumaker.generation.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.FactKind
import java.math.BigDecimal

/**
 * 텍스트에서 **정량 수치(NUMERIC)·고유명사(PROPER_NOUN) 후보 토큰**을 결정적으로 추출하는 공유 추출기
 * (도메인 이해 §421~429의 §425 "산출물 텍스트에서 직접 추출").
 *
 * **추출 진실의 원천 일원화:** 기존 [DeterministicGroundingValidator](Cycle C 신뢰성 검증)와 품질 개선의
 * **원본 사실 토큰 보존 검증**(QC4 — [watson.resumaker.quality.application.FactTokenPreservationValidator])이
 * **동일한 추출·정규화 규칙**을 공유해야 한다(보존 검증이 검증기보다 느슨하면 "다듬다 흘린 사실"을 놓친다).
 * 그래서 추출/정규화/경계 매칭 로직을 여기로 모은다. 순수·결정적이며 외부 호출(LLM·네트워크·시계)이 없다.
 *
 * ## NUMERIC 추출·정규화 규칙·한계(문서화 — §426)
 * - 추출: 텍스트에서 숫자 토큰을 뽑는다. 천단위 콤마(`12,000`)·소수점(`3.14`)·퍼센트/단위 접미는 숫자 본체만 취한다.
 * - 정규화: 콤마 제거 후 [BigDecimal]로 파싱해 **수치 값**으로 비교한다(스케일 무시: `3.0` ≡ `3`).
 *   그래서 표기 차이가 있어도 같은 값이면 동치다: `40%` ≡ `40 퍼센트` ≡ `40`, `12,000` ≡ `12000`.
 * - **알려진 한계(단위 충돌 오음성):** 판정은 단위를 무시한 순수 값 동치다(`40%`가 출처 `40명`으로도 통과).
 *   단위가 다른 값을 같은 근거로 보는 오음성은 의도된 트레이드오프다(한국어에서 결정적 단위 정규화 불가).
 *
 * ## PROPER_NOUN 추출·정규화 규칙·한계(문서화 — §426)
 * 한국어 고유명사의 결정적 추출은 근본적으로 어렵다(형태소·사전 없이 완전 추출 불가). 균형을 위해
 * **결정적이고 보수적인** 후보 규칙만 채택한다:
 * - **라틴 알파벳/숫자 혼합 토큰**(영문 기술명·제품명 패턴): 예 `Kotlin`, `AWS`, `K8s`, `GPT-4`.
 *   공백 구분 단어를 greedy 병합하지 않는다(각 단어가 독립 후보 — 융합·과병합 방지).
 * - **따옴표로 인용된 토큰**: `"..."`, `'...'`, `『...』`, `「...」` 안의 짧은 인용(프로젝트명 등).
 * - 추출하지 않는 것(한계): 따옴표 없는 **순수 한글 고유명사**(회사명 등)는 일반 한글 명사와 결정적으로
 *   구분할 수 없어 자동 추출 대상에서 제외한다(§427과 동일한 한계 — 의도된 트레이드오프).
 *
 * ### 정규화·매칭(경계 일치 — 오음성 차단)
 * - 정규화: 코퍼스·후보 모두 **소문자화 + 공백/개행을 단일 공백으로 collapse**한다(삭제하지 않음).
 * - 매칭: 라틴 후보는 **단어 경계 기준**으로 코퍼스에 존재할 때만 근거 있음으로 본다. 짧은 토큰("Go","R")이
 *   더 긴 단어("Google","cargo")의 부분 문자열로 통과하지 못한다. 따옴표 인용 후보는 **경계 포함 부분 문자열**로 대조한다.
 * - **알려진 한계(따옴표 길이 초과 드롭):** [MAX_QUOTED_NOUN_LENGTH](40자)를 넘는 인용구는 후보에서 제외한다.
 */
@Component
class FactTokenExtractor {

    // ----- 추출(원문 토큰 단위) -----

    /**
     * [text]에서 사실 토큰 후보를 추출한다(NUMERIC + PROPER_NOUN). 보존 검증(QC4)이 "원본의 사실 토큰 집합"을
     * 만드는 데 쓴다. 동일 정규화 값은 한 번만 담는다(원문 표기 중 첫 등장을 대표로 둔다).
     */
    fun extract(text: String): List<ExtractedToken> {
        val tokens = mutableListOf<ExtractedToken>()
        tokens += extractNumericTokens(text).map { (raw, value) ->
            ExtractedToken(text = raw, kind = FactKind.NUMERIC, normalized = value.toPlainString())
        }
        extractProperNounCandidates(text).forEach { (candidate, _) ->
            val normalized = normalizeForNoun(candidate)
            if (normalized.isNotBlank()) {
                tokens += ExtractedToken(text = candidate, kind = FactKind.PROPER_NOUN, normalized = normalized)
            }
        }
        return tokens
    }

    /** content에서 (원문토큰, 정규화 값) 쌍을 추출한다. */
    fun extractNumericTokens(text: String): List<Pair<String, BigDecimal>> =
        NUMBER_REGEX.findAll(text).mapNotNull { match ->
            val raw = match.value
            normalizeNumber(raw)?.let { raw to it }
        }.toList()

    /** content에서 정규화한 숫자 값 집합을 만든다(코퍼스 인덱싱·근거 대조용). */
    fun extractNumericValues(text: String): Set<BigDecimal> =
        NUMBER_REGEX.findAll(text).mapNotNull { normalizeNumber(it.value) }.toSet()

    /**
     * 고유명사 후보 목록. 각 원소는 (원문 후보, 라틴 단어 여부). 라틴 단어는 공백 구분 단어 *하나*씩 독립 후보로 둔다
     * (greedy 병합 금지). 따옴표 인용은 정규화 후 다단어·한글 포함도 한 단위로 둔다.
     */
    fun extractProperNounCandidates(text: String): List<Pair<String, Boolean>> {
        val candidates = mutableListOf<Pair<String, Boolean>>()
        LATIN_WORD_REGEX.findAll(text).forEach { candidates += it.value to true }
        QUOTED_REGEX.findAll(text).forEach { match ->
            val inner = match.groupValues[1].trim()
            // 따옴표 인용이 단일 라틴 단어면 위에서 이미 잡으므로, 다단어·한글 포함 인용(프로젝트명 등)을 한 단위로 본다.
            if (inner.isNotBlank() && inner.length <= MAX_QUOTED_NOUN_LENGTH) candidates += inner to false
        }
        return candidates.filter { it.first.isNotBlank() }
    }

    // ----- 정규화 -----

    /** 콤마 제거 후 BigDecimal 값으로 정규화(스케일 무시: 3.0 ≡ 3). 실패 시 null. */
    fun normalizeNumber(raw: String): BigDecimal? =
        runCatching { BigDecimal(raw.replace(",", "")).stripTrailingZeros() }.getOrNull()

    /** 소문자화 + 공백/개행을 단일 공백으로 collapse(삭제하지 않음). 양끝 공백 제거. */
    fun normalizeForNoun(text: String): String =
        text.lowercase().replace(WHITESPACE_REGEX, " ").trim()

    // ----- 경계 매칭 -----

    /**
     * [word]가 [corpus]에 **단어 경계**로 존재하는지. 경계 = 양옆이 라틴 *알파벳*이 아닌 위치.
     * - `go`가 `google`/`cargo`(양옆 알파벳)에 매칭되지 않고, 떨어진 `react`·`flow`가 융합 `reactflow`의 근거가 되지 않는다.
     * - 숫자는 경계로 본다(단위 접미가 숫자에 붙는 `30ms`·`16gb`의 `ms`·`gb`가 경계 일치로 통과 — 흔한 정당 케이스).
     *
     * [corpus]·[word]는 모두 정규화된([normalizeForNoun]) 문자열이어야 한다.
     */
    fun containsLatinWord(corpus: String, word: String): Boolean {
        var from = 0
        while (true) {
            val idx = corpus.indexOf(word, from)
            if (idx < 0) return false
            val before = idx - 1
            val after = idx + word.length
            val leftOk = before < 0 || !corpus[before].isLatinLetter()
            val rightOk = after >= corpus.length || !corpus[after].isLatinLetter()
            if (leftOk && rightOk) return true
            from = idx + 1
        }
    }

    /**
     * 따옴표 인용 후보 매칭. 정규화(단일 공백) 후 **경계 포함 부분 문자열**로 본다: 양끝이 라틴 알파벳이면 그 끝에
     * 단어 경계를 요구하고, 한글 등 비라틴 끝이면 위치 무관(한국어는 토큰 경계가 공백과 무관하므로).
     *
     * [corpus]·[phrase]는 모두 정규화된([normalizeForNoun]) 문자열이어야 한다.
     */
    fun containsQuotedPhrase(corpus: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val idx = corpus.indexOf(phrase, from)
            if (idx < 0) return false
            val before = idx - 1
            val after = idx + phrase.length
            val leftOk = !phrase.first().isLatinLetter() ||
                before < 0 || !corpus[before].isLatinLetter()
            val rightOk = !phrase.last().isLatinLetter() ||
                after >= corpus.length || !corpus[after].isLatinLetter()
            if (leftOk && rightOk) return true
            from = idx + 1
        }
    }

    private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    companion object {
        /** 정수/소수/천단위 콤마 수치 토큰. 앞뒤 단위·기호는 별도(값만 비교). */
        private val NUMBER_REGEX = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""")

        /**
         * 라틴 알파벳을 포함하는 **단어 하나**(영문 기술명/제품명 패턴). 공백/한글/일반 구두점 경계로 끊는다.
         * 공백 구분 단어를 greedy 병합하지 않는다(각 단어가 독립 후보 — 융합·과병합 방지).
         * 단어 첫 글자는 알파벳이어야 후보다(순수 숫자는 NUMERIC이 다룬다).
         */
        private val LATIN_WORD_REGEX = Regex("""[A-Za-z][A-Za-z0-9.+#/-]*""")

        /** 따옴표 인용(작은·큰따옴표, 한글 인용부호). */
        private val QUOTED_REGEX = Regex("""[\"'「『]([^\"'」』]{1,40})[\"'」』]""")

        private val WHITESPACE_REGEX = Regex("""\s+""")

        const val MAX_QUOTED_NOUN_LENGTH = 40
    }
}

/**
 * 추출된 한 사실 토큰. [normalized]는 동치 비교 키(NUMERIC=BigDecimal plain 문자열, PROPER_NOUN=정규화 텍스트).
 * 보존 검증(QC4)이 원본·후보의 정규화 토큰 집합을 비교하는 데 쓴다.
 */
data class ExtractedToken(
    val text: String,
    val kind: FactKind,
    val normalized: String,
)
