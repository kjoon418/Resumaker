package watson.resumaker.quality.application

import org.springframework.stereotype.Component
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.FactTokenExtractor

/**
 * 원본 사실 토큰 보존 검증(품질 개선 기획 §3.5·§180·§196, 수용 기준 QC4 — **개선 특유 불변식**).
 *
 * 1차 생성의 신뢰성 검증([watson.resumaker.generation.application.DeterministicGroundingValidator])은 "근거 없는 새
 * 사실 추가 0건"을 막는다(QC3·QC9). 품질 개선은 거기에 더해 "다듬다가 **원본의 사실을 흘리거나 바꾸지 않았는가**"를
 * 본다: 다듬기는 표현만 바꿔야 하므로, 원본 항목의 **고유명사 토큰**은 후보에도 전수 보존돼야 한다.
 *
 * ## 수치 토큰은 "변형·날조 금지"로 좁힌다(AI-02 — 길이/압축 처치와의 충돌 해소)
 * 수치 토큰까지 "원본 ⊆ 후보(전수 보존)"를 강제하면 길이/압축 처치(군더더기 파생 수치 제거)가 항상 보존 실패로
 * 막혀 처치가 사실상 no-op이 된다. QC4의 본뜻은 "수치를 **변형·날조**하지 말 것"이므로, 수치는 **"후보 ⊆ 원본
 * (후보에 원본에 없던 새 수치 0건)"**으로 판정한다: 다듬기가 수치를 **새로 만들지만 않으면** 통과하고, 파생 수치
 * 축약(원본에만 있던 수치 제거)은 허용한다. 고유명사는 변형 시 의미가 달라지므로 전수 보존을 유지한다.
 *
 * 판정: 원본·후보 텍스트에서 [FactTokenExtractor]로 동일 규칙의 사실 토큰을 추출해 **정규화 집합**으로 비교한다.
 * 고유명사는 원본의 토큰이 후보에 모두 있어야 하고, 수치는 후보의 토큰이 모두 원본에 있어야 한다(새 수치 0건). 검증기와
 * 같은 추출 규칙을 공유한다(추출 일원화). 후보가 새로 더한 **고유명사**의 정당성(근거 유무)은 보존 검증이 아니라 1차 생성
 * 신뢰성 검증의 책임이다.
 *
 * ## skillTags 사전 보강(M2 — 한글 고유명사 사각 완화)
 * [FactTokenExtractor]는 따옴표 없는 **순수 한글 고유명사**(한글 기술명·회사명 등)를 결정적으로 추출하지 못한다
 * (일반 한글 명사와 구별 불가 — 추출기 주석 참고). 그래서 다듬기가 한글 기술명("스프링부트"·"카프카")을 누락·변형해도
 * 위 사실 토큰 비교로는 못 잡는다. 보존 검증은 **원본(이미 근거 통과)과 출처 경험을 둘 다 갖고 있으므로**, 사용자가
 * 직접 선언한 [ExperienceSnapshot.skillTags]를 **고유명사 사전**으로 써서 이 사각을 보완한다: 원본에 등장한 skillTag가
 * 후보에서 사라지면 보존 실패로 본다. skillTag는 사용자가 명시한 값이라 거짓 양성 위험이 거의 없고, 검증을 **더 엄격하게**
 * 만들 뿐이라(느슨해지지 않으므로) 공유 추출기·1차 생성 검증을 건드리지 않는다. 보존 검증의 거짓 양성은 "원본 유지"라는
 * 안전한 실패라서, 과보존 쪽으로 편향해도 해가 없다.
 *
 * 순수·결정적이며 외부 호출이 없다(같은 입력 → 같은 결과).
 */
@Component
class FactTokenPreservationValidator(
    private val extractor: FactTokenExtractor,
) {

    /**
     * 후보가 원본의 사실 토큰(+ 원본에 등장한 skillTag 고유명사)을 모두 보존하는지 판정한다.
     *
     * @param experiences 항목 출처 경험. skillTags를 한글 고유명사 보강 사전으로 쓴다(M2). 비우면 사실 토큰만 본다.
     * @return 보존됐으면 true. 빠진 토큰이 하나라도 있으면 false.
     */
    fun preserves(
        original: String,
        candidate: String,
        experiences: List<ExperienceSnapshot> = emptyList(),
    ): Boolean = missingTokens(original, candidate, experiences).isEmpty()

    /**
     * 보존 위반 토큰의 정규화 값 목록(디버깅·표시용). 비어 있으면 보존.
     * - 고유명사: 원본에 있는데 후보에서 빠진 토큰(누락·변형).
     * - 수치: 후보에 있는데 원본에 없던 토큰(새 수치 날조). 파생 수치 축약(원본에만 있던 수치 제거)은 위반이 아니다(AI-02).
     */
    fun missingTokens(
        original: String,
        candidate: String,
        experiences: List<ExperienceSnapshot> = emptyList(),
    ): List<String> {
        val originalTokens = extractor.extract(original)
        val candidateTokens = extractor.extract(candidate)

        // 고유명사: 전수 보존(원본 ⊆ 후보). 빠지면 위반.
        val originalNouns = originalTokens.filter { it.kind == FactKind.PROPER_NOUN }.map { it.normalized }.toSet()
        val candidateNouns = candidateTokens.filter { it.kind == FactKind.PROPER_NOUN }.map { it.normalized }.toSet()
        val missingNouns = originalNouns - candidateNouns

        // 수치: 변형·날조 금지로 완화(후보 ⊆ 원본). 후보에만 있는 새 수치만 위반, 원본 수치 축약은 허용.
        val originalNumerics = originalTokens.filter { it.kind == FactKind.NUMERIC }.map { it.normalized }.toSet()
        val candidateNumerics = candidateTokens.filter { it.kind == FactKind.NUMERIC }.map { it.normalized }.toSet()
        val fabricatedNumerics = candidateNumerics - originalNumerics

        val missingFactTokens = missingNouns + fabricatedNumerics

        // M2: 사용자 선언 skillTags를 고유명사 사전으로. 원본에 있던 태그가 후보에서 사라지면 누락으로 본다.
        // 추출기의 경계 포함 매칭(라틴 끝은 단어경계, 한글 끝은 위치 무관)을 재사용해 짧은 토큰 오매칭을 막는다.
        val normalizedOriginal = extractor.normalizeForNoun(original)
        val normalizedCandidate = extractor.normalizeForNoun(candidate)
        val missingSkillTags = experiences
            .flatMap { it.skillTags }
            .map { extractor.normalizeForNoun(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { tag ->
                extractor.containsQuotedPhrase(normalizedOriginal, tag) &&
                    !extractor.containsQuotedPhrase(normalizedCandidate, tag)
            }

        return (missingFactTokens + missingSkillTags).distinct()
    }
}
