package watson.resumaker.generation.application

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.FactKind

/**
 * [GroundingValidator]의 결정적 구현(구현 설계 §6, 도메인 이해 §421~429). **Cycle C.**
 *
 * 순수·결정적이며 외부 호출(LLM·네트워크·시계)이 없다. 같은 입력에 항상 같은 결과를 돌려준다.
 *
 * ## 판정 절차
 * 1. 항목의 출처 경험 스냅샷(section.sourceExperienceIds로 좁힌 [sources])에서 **근거 본문(corpus)** 을 만든다.
 *    경험의 title/body/situation/action/result/skillTags를 모두 합친다(사용자가 적은 모든 본문이 근거).
 * 2. 산출물 content에서 **독립적으로** 수치·고유명사 토큰을 추출한다(AI 신고 factGroundings 미신뢰 — §425).
 * 3. 추출된 각 토큰이 corpus에 **문자적 근거**가 있는지 결정적으로 대조한다. 하나라도 근거가 없으면 실패.
 *
 * ## NUMERIC 추출·정규화 규칙·한계(문서화 — §426)
 * - 추출: content에서 숫자 토큰을 뽑는다. 천단위 콤마(`12,000`)·소수점(`3.14`)·퍼센트/단위 접미는 숫자 본체만 취한다.
 * - 정규화: 콤마 제거 후 [java.math.BigDecimal]로 파싱해 **수치 값**으로 비교한다. 그래서 표기 차이가 있어도
 *   같은 값이면 동치다: `40%` ≡ `40 퍼센트` ≡ `40`(추출 단계에서 단위/기호를 떼고 값만 비교), `12,000` ≡ `12000`,
 *   `3.0` ≡ `3`. corpus에서도 같은 규칙으로 숫자 값 집합을 만들어, content의 수치 값이 그 집합에 있으면 근거 있음.
 * - 근거 판정은 **값 동치**다. 산출물의 한 수치 값이 출처 본문 어디에도 같은 값으로 없으면 근거 없는 수치(실패).
 * - **알려진 한계(단위 충돌 오음성):** 판정은 단위를 무시한 순수 값 동치다. 따라서 산출물 "40%"가 출처 "40명"
 *   으로도 통과한다(값 40이 출처에 존재하므로). 단위가 다른 값을 같은 근거로 보는 오음성은 의도된 트레이드오프다
 *   (단위 정규화는 결정적 사전 없이 한국어에서 신뢰성 있게 불가). 이 동작은 회귀 테스트로 고정돼 우발적 변경을 막는다.
 *
 * ## PROPER_NOUN 추출·정규화 규칙·한계(문서화 — §426)
 * 한국어 고유명사의 결정적 추출은 근본적으로 어렵다(형태소·사전 없이 완전 추출 불가). 과검출로 자유 서술을
 * 오탐하면 안 되고, 미검출로 날조 고유명사를 놓치면 신뢰성이 깨진다. 균형을 위해 **결정적이고 보수적인**
 * 후보 규칙만 채택한다:
 * - **라틴 알파벳/숫자 혼합 토큰**(영문 기술명·제품명 패턴): 예 `Kotlin`, `AWS`, `K8s`, `GPT-4`.
 *   한글이 아닌 알파벳을 포함한 **단어 하나**를 후보로 본다(공백 구분 단어를 한 덩어리로 병합하지 않는다 —
 *   각 단어가 독립 후보다). 영문 고유명사는 자유 서술과 잘 섞이지 않아 오탐이 낮다.
 * - **따옴표로 인용된 토큰**: `"..."`, `'...'`, `『...』`, `「...」` 안의 짧은 인용(프로젝트명 등).
 * - 추출하지 않는 것(한계): 따옴표 없는 **순수 한글 고유명사**(회사명 등)는 일반 한글 명사와 결정적으로
 *   구분할 수 없어 자동 추출 대상에서 제외한다. 이런 항목의 날조는 생성 지시(프롬프트)와 사용자 검토로 다룬다
 *   (§427과 동일한 한계 — 자유 서술 제외 논리). 이 한계는 의도된 트레이드오프다.
 *
 * ### 정규화·매칭(경계 일치 — 오음성 차단)
 * - 정규화: 코퍼스·후보 모두 **소문자화 + 공백/개행을 단일 공백으로 collapse**한다. 공백을 *삭제하지 않는다*.
 *   (옛 구현은 공백을 전부 제거해 `react\nflow` → `reactflow`로 융합돼 날조 "ReactFlow"가 통과했다 — 오음성.)
 * - 매칭: 라틴 후보는 **단어 경계 기준**으로 코퍼스에 존재할 때만 근거 있음으로 본다. 짧은 토큰("Go","R")이
 *   더 긴 단어("Google","cargo")의 부분 문자열로 통과하지 못한다. 코퍼스에 떨어져 있는 "react"·"flow"가
 *   날조된 한 단어 "reactflow"의 근거가 되지 못한다(융합 매칭 제거).
 * - 다단어 라틴 이름: 따옴표 없는 공백 구분 라틴 단어들은 **각각 독립 후보**로 매칭한다(예 "Spring Boot"는
 *   "spring"과 "boot"가 각각 경계 일치하면 통과). 따옴표 인용구는 정규화 후 **경계 포함 부분 문자열**로 대조한다
 *   (인용 다단어 제품명·한글 포함 프로젝트명을 한 단위로 다루기 위함 — §426 문자적 존재 의도).
 * - **알려진 한계(따옴표 길이 초과 드롭):** [MAX_QUOTED_NOUN_LENGTH](40자)를 넘는 인용구는 후보에서 제외한다
 *   (긴 자유 서술 인용을 고유명사로 오탐하지 않기 위함). 이 미검출은 의도된 트레이드오프다.
 *
 * 두 범주만 자동 실패 판정 대상이다(§423). 성과 주장 등 자유 서술은 추출 규칙에 걸리지 않아 자동 판정에서 제외된다.
 */
@Primary
@Component
class DeterministicGroundingValidator : GroundingValidator {

    override fun validate(section: GeneratedSection, sources: List<ExperienceSnapshot>): SectionValidationResult {
        // 항목의 출처 경험으로 corpus를 좁힌다(다른 경험 본문을 근거로 오인하지 않게).
        val sourceIdSet = section.sourceExperienceIds.toSet()
        val relevant = sources.filter { it.id in sourceIdSet }
        // 출처가 비어 있으면(층위1 누락) 대조할 근거가 없으므로, content에 토큰이 하나라도 있으면 근거 없음으로 본다.
        val corpus = relevant.joinToString("\n") { it.toCorpusText() }

        val ungrounded = mutableListOf<UngroundedToken>()
        ungrounded += findUngroundedNumerics(section.content, corpus)
        ungrounded += findUngroundedProperNouns(section.content, corpus)

        return SectionValidationResult(
            definitionKey = section.definitionKey,
            valid = ungrounded.isEmpty(),
            ungroundedTokens = ungrounded,
        )
    }

    // ----- NUMERIC -----

    private fun findUngroundedNumerics(content: String, corpus: String): List<UngroundedToken> {
        val corpusValues = extractNumericValues(corpus)
        return extractNumericTokens(content).mapNotNull { (raw, value) ->
            if (value in corpusValues) null
            else UngroundedToken(text = raw, kind = FactKind.NUMERIC)
        }
    }

    /** content에서 (원문토큰, 정규화 값) 쌍을 추출한다. */
    private fun extractNumericTokens(text: String): List<Pair<String, java.math.BigDecimal>> =
        NUMBER_REGEX.findAll(text).mapNotNull { match ->
            val raw = match.value
            normalizeNumber(raw)?.let { raw to it }
        }.toList()

    private fun extractNumericValues(text: String): Set<java.math.BigDecimal> =
        NUMBER_REGEX.findAll(text).mapNotNull { normalizeNumber(it.value) }.toSet()

    /** 콤마 제거 후 BigDecimal 값으로 정규화(스케일 무시: 3.0 ≡ 3). 실패 시 null. */
    private fun normalizeNumber(raw: String): java.math.BigDecimal? =
        runCatching { java.math.BigDecimal(raw.replace(",", "")).stripTrailingZeros() }.getOrNull()

    // ----- PROPER_NOUN -----

    /**
     * 정규화한 코퍼스(소문자 + 단일 공백 collapse, 융합 없음)에서 각 후보가 **경계 일치**로 존재하는지 본다.
     * 라틴 후보는 단어 경계 매칭(짧은 토큰의 부분 문자열 통과·공백 융합 통과를 모두 차단), 따옴표 인용 후보는
     * 경계 포함 부분 문자열 매칭(다단어·한글 포함 인용을 한 단위로 다룸).
     */
    private fun findUngroundedProperNouns(content: String, corpus: String): List<UngroundedToken> {
        val normalizedCorpus = normalizeForNoun(corpus)
        val seen = mutableSetOf<String>()
        return extractProperNounCandidates(content).mapNotNull { (candidate, latinWord) ->
            val normalized = normalizeForNoun(candidate)
            if (normalized.isBlank() || !seen.add(normalized)) return@mapNotNull null
            val grounded =
                if (latinWord) containsLatinWord(normalizedCorpus, normalized)
                else containsQuotedPhrase(normalizedCorpus, normalized)
            if (grounded) null else UngroundedToken(text = candidate, kind = FactKind.PROPER_NOUN)
        }
    }

    /**
     * 후보 목록. 각 원소는 (원문 후보, 라틴 단어 여부). 라틴 단어는 공백 구분 단어 *하나*씩 독립 후보로 둔다
     * (greedy 병합 금지). 따옴표 인용은 정규화 후 다단어·한글 포함도 한 단위로 둔다.
     */
    private fun extractProperNounCandidates(text: String): List<Pair<String, Boolean>> {
        val candidates = mutableListOf<Pair<String, Boolean>>()
        LATIN_WORD_REGEX.findAll(text).forEach { candidates += it.value to true }
        QUOTED_REGEX.findAll(text).forEach { match ->
            val inner = match.groupValues[1].trim()
            // 따옴표 인용이 단일 라틴 단어면 위에서 이미 잡으므로, 다단어·한글 포함 인용(프로젝트명 등)을 한 단위로 본다.
            if (inner.isNotBlank() && inner.length <= MAX_QUOTED_NOUN_LENGTH) candidates += inner to false
        }
        return candidates.filter { it.first.isNotBlank() }
    }

    /** 소문자화 + 공백/개행을 단일 공백으로 collapse(삭제하지 않음). 양끝 공백 제거. */
    private fun normalizeForNoun(text: String): String =
        text.lowercase().replace(WHITESPACE_REGEX, " ").trim()

    /**
     * [word]가 [corpus]에 **단어 경계**로 존재하는지. 경계 = 양옆이 라틴 *알파벳*이 아닌 위치.
     * - `go`가 `google`/`cargo`(양옆 알파벳)에 매칭되지 않고, 떨어진 `react`·`flow`가 융합 `reactflow`의 근거가 되지 않는다.
     * - 숫자는 경계로 본다(단위 접미가 숫자에 붙는 `30ms`·`16gb`의 `ms`·`gb`가 경계 일치로 통과 — 흔한 정당 케이스).
     */
    private fun containsLatinWord(corpus: String, word: String): Boolean {
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
     */
    private fun containsQuotedPhrase(corpus: String, phrase: String): Boolean {
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

    private fun ExperienceSnapshot.toCorpusText(): String = buildString {
        append(title).append('\n')
        append(body).append('\n')
        situation?.let { append(it).append('\n') }
        action?.let { append(it).append('\n') }
        result?.let { append(it).append('\n') }
        if (skillTags.isNotEmpty()) append(skillTags.joinToString(" "))
    }

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

        private const val MAX_QUOTED_NOUN_LENGTH = 40
    }
}
