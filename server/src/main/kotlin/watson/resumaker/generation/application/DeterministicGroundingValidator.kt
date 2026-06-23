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
 * **추출·정규화·경계 매칭 규칙은 [FactTokenExtractor]에 일원화**한다(품질 개선의 원본 사실 토큰 보존 검증 QC4와
 * 동일 규칙 공유 — 보존 검증이 더 느슨하면 "다듬다 흘린 사실"을 놓치므로 한 진실의 원천을 둔다). NUMERIC 단위 충돌
 * 오음성·PROPER_NOUN 한글 미검출·따옴표 길이 초과 드롭 등 한계 문서는 [FactTokenExtractor] 주석을 참조한다.
 *
 * 두 범주(NUMERIC·PROPER_NOUN)만 자동 실패 판정 대상이다(§423). 성과 주장 등 자유 서술은 추출 규칙에 걸리지
 * 않아 자동 판정에서 제외된다.
 */
@Primary
@Component
class DeterministicGroundingValidator(
    // 운영에서는 스프링이 주입한다. 기존 단위 테스트가 인자 없이 생성하므로 기본값을 둔다(동작 동일 — 순수 추출기).
    private val extractor: FactTokenExtractor = FactTokenExtractor(),
) : GroundingValidator {

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
        val corpusValues = extractor.extractNumericValues(corpus)
        return extractor.extractNumericTokens(content).mapNotNull { (raw, value) ->
            if (value in corpusValues) null
            else UngroundedToken(text = raw, kind = FactKind.NUMERIC)
        }
    }

    // ----- PROPER_NOUN -----

    /**
     * 정규화한 코퍼스(소문자 + 단일 공백 collapse, 융합 없음)에서 각 후보가 **경계 일치**로 존재하는지 본다.
     * 라틴 후보는 단어 경계 매칭(짧은 토큰의 부분 문자열 통과·공백 융합 통과를 모두 차단), 따옴표 인용 후보는
     * 경계 포함 부분 문자열 매칭(다단어·한글 포함 인용을 한 단위로 다룸). 규칙은 [FactTokenExtractor]가 소유한다.
     */
    private fun findUngroundedProperNouns(content: String, corpus: String): List<UngroundedToken> {
        val normalizedCorpus = extractor.normalizeForNoun(corpus)
        val seen = mutableSetOf<String>()
        return extractor.extractProperNounCandidates(content).mapNotNull { (candidate, latinWord) ->
            val normalized = extractor.normalizeForNoun(candidate)
            if (normalized.isBlank() || !seen.add(normalized)) return@mapNotNull null
            val grounded =
                if (latinWord) extractor.containsLatinWord(normalizedCorpus, normalized)
                else extractor.containsQuotedPhrase(normalizedCorpus, normalized)
            if (grounded) null else UngroundedToken(text = candidate, kind = FactKind.PROPER_NOUN)
        }
    }

    private fun ExperienceSnapshot.toCorpusText(): String = buildString {
        append(title).append('\n')
        append(body).append('\n')
        situation?.let { append(it).append('\n') }
        action?.let { append(it).append('\n') }
        result?.let { append(it).append('\n') }
        if (skillTags.isNotEmpty()) append(skillTags.joinToString(" "))
    }
}
