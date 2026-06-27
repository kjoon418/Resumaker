package watson.resumaker.generation.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

/**
 * [DeterministicGroundingValidator] 단위 테스트(수용 기준 17·18, 도메인 이해 §421~429).
 *
 * 결정적·외부 호출 없음. 산출물 content에서 독립 추출한 수치·고유명사를 출처 경험 본문과 대조해 판정한다.
 */
class DeterministicGroundingValidatorTest {

    private val validator = DeterministicGroundingValidator()

    private val expId = ExperienceRecordId(UUID.randomUUID())

    private fun source(
        body: String,
        id: ExperienceRecordId = expId,
        title: String = "경험",
        situation: String? = null,
        action: String? = null,
        result: String? = null,
        skillTags: List<String> = emptyList(),
    ) = ExperienceSnapshot(
        id = id,
        title = title,
        body = body,
        situation = situation,
        action = action,
        result = result,
        skillTags = skillTags,
    )

    private fun section(
        content: String,
        sources: List<ExperienceRecordId> = listOf(expId),
    ) = GeneratedSection(
        definitionKey = "section-0-요약",
        sectionKind = SectionKind.SUMMARY,
        content = content,
        succeeded = true,
        sourceExperienceIds = sources,
        factGroundings = emptyList(),
    )

    @Test
    fun 근거_있는_수치는_통과한다() {
        // given — 출처 본문에 "40%"가 그대로 있다.
        val result = validator.validate(
            section("응답 속도를 40% 단축했다."),
            listOf(source(body = "응답 속도를 40% 줄였다.")),
        )

        // then
        assertThat(result.valid).isTrue()
        assertThat(result.ungroundedTokens).isEmpty()
    }

    @Test
    fun 근거_없는_수치는_검출된다() {
        // given (도메인 예시 §415) — 출처엔 "응답 속도를 줄였다"만, 산출물은 "40% 단축".
        val result = validator.validate(
            section("응답 속도를 40% 단축했다."),
            listOf(source(body = "응답 속도를 줄였다.")),
        )

        // then — 40이 출처에 없어 검출.
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).containsOnly(FactKind.NUMERIC)
        assertThat(result.ungroundedTokens.map { it.text }).contains("40")
    }

    @Test
    fun 표기_차이_퍼센트_표기는_동치로_통과한다() {
        // given — 산출물 "40%" vs 출처 "40 퍼센트" — 같은 값.
        val result = validator.validate(
            section("성능을 40% 개선했다."),
            listOf(source(body = "성능을 40 퍼센트 개선했다.")),
        )

        // then — 숫자 값 40이 양쪽에 존재 → 통과.
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 표기_차이_천단위_콤마는_동치로_통과한다() {
        // given — 산출물 "12,000건" vs 출처 "12000건".
        val result = validator.validate(
            section("12,000건을 처리했다."),
            listOf(source(body = "12000건을 처리했다.")),
        )

        // then
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 근거_있는_고유명사_기술명은_통과한다() {
        // given — "Kotlin"이 출처에 있다.
        val result = validator.validate(
            section("Kotlin으로 백엔드를 구현했다."),
            listOf(source(body = "Kotlin과 Spring으로 개발")),
        )

        // then
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 근거_없는_고유명사_기술명은_검출된다() {
        // given — 산출물에 "GraphQL"이 있으나 출처엔 없다.
        val result = validator.validate(
            section("GraphQL API를 설계했다."),
            listOf(source(body = "REST API를 설계했다.")),
        )

        // then
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).contains(FactKind.PROPER_NOUN)
        assertThat(result.ungroundedTokens.map { it.text.lowercase() }).anyMatch { it.contains("graphql") }
    }

    @Test
    fun 고유명사_대소문자_차이는_동치로_통과한다() {
        // given — 산출물 "AWS" vs 출처 "aws".
        val result = validator.validate(
            section("AWS에 배포했다."),
            listOf(source(body = "aws 환경에 배포")),
        )

        // then
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 자유_서술_성과_주장은_자동_판정_대상에서_제외된다() {
        // given — 수치·고유명사 없는 순수 한글 성과 주장. 추출 규칙에 걸리지 않아야 한다(§427).
        val result = validator.validate(
            section("팀의 협업 문화를 크게 개선하여 생산성을 높였다."),
            listOf(source(body = "협업 방식을 바꿨다.")),
        )

        // then — 자동 판정 대상 토큰이 없어 통과(날조는 생성 지시·사용자 검토 책임).
        assertThat(result.valid).isTrue()
        assertThat(result.ungroundedTokens).isEmpty()
    }

    @Test
    fun 순수_한글_고유명사는_추출하지_않는다_한계_문서화() {
        // given — 따옴표 없는 한글 회사명은 일반 한글과 구분 불가라 자동 추출 제외(의도된 한계).
        val result = validator.validate(
            section("토스에서 결제 시스템을 만들었다."),
            listOf(source(body = "결제 시스템을 만들었다.")),
        )

        // then — "토스"는 추출되지 않아 통과(미검출 — 문서화된 한계).
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 출처가_여러_경험일_때_해당_항목_출처_본문만_대조한다() {
        // given — content의 수치 50은 출처(exp1) 본문엔 없고, 무관한 경험(exp2)에만 있다.
        val exp2 = ExperienceRecordId(UUID.randomUUID())
        val result = validator.validate(
            section("처리량을 50% 늘렸다.", sources = listOf(expId)),
            listOf(
                source(body = "처리량을 늘렸다.", id = expId),
                source(body = "50% 향상", id = exp2),
            ),
        )

        // then — 항목 출처(exp1)에 50이 없으므로 검출(다른 경험 근거 오인 방지).
        assertThat(result.valid).isFalse()
    }

    @Test
    fun 근거가_situation_action_result_skillTags에_있어도_통과한다() {
        // given — 근거가 본문이 아닌 STAR/역량 필드에 있는 경우도 corpus에 포함.
        val result = validator.validate(
            section("Redis로 캐시를 도입해 30ms를 단축했다."),
            listOf(
                source(
                    body = "캐시를 도입했다.",
                    action = "Redis 캐시 적용",
                    result = "응답을 30ms 단축",
                ),
            ),
        )

        // then
        assertThat(result.valid).isTrue()
    }

    // ----- [MED-2 + MED-3 + OQ-1] PROPER_NOUN 오음성(날조 고유명사 통과) 차단 회귀 -----

    @Test
    fun 짧은_라틴_토큰은_더_긴_단어의_부분문자열로_통과하지_않는다() {
        // given — 산출물에 "Go"가 있으나, 출처엔 "Google"·"cargo"만 있다("go"가 substring으로 들어 있음).
        val result = validator.validate(
            section("Go로 마이크로서비스를 구현했다."),
            listOf(source(body = "Google Cloud와 cargo 빌드를 사용했다.")),
        )

        // then — 단어 경계 매칭이라 "Go"가 "Google"/"cargo"에 묻혀 통과하지 못하고 검출된다(오음성 차단).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).contains(FactKind.PROPER_NOUN)
        assertThat(result.ungroundedTokens.map { it.text }).contains("Go")
    }

    @Test
    fun 단어_경계로_근거_있는_짧은_라틴_토큰은_통과한다() {
        // given — "Go"가 출처에 독립 단어로 존재한다.
        val result = validator.validate(
            section("Go로 마이크로서비스를 구현했다."),
            listOf(source(body = "Go 언어로 서버를 만들었다.")),
        )

        // then — 경계 일치라 통과.
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 공백_융합_날조_고유명사는_검출된다() {
        // given — 출처엔 "React"와 "Flow"가 줄/단어로 떨어져 있고, 산출물은 융합된 날조 "ReactFlow".
        val result = validator.validate(
            section("ReactFlow로 다이어그램을 그렸다."),
            listOf(source(body = "React 컴포넌트", action = "Flow 차트를 그렸다.")),
        )

        // then — 공백을 삭제하지 않고 collapse하므로 "react"·"flow"가 "reactflow"로 융합되지 않아 검출(오음성 차단).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).contains(FactKind.PROPER_NOUN)
        assertThat(result.ungroundedTokens.map { it.text }).contains("ReactFlow")
    }

    @Test
    fun 다단어_라틴_이름은_각_단어가_경계_일치하면_통과한다() {
        // given — "Spring Boot"가 출처에 그대로 있고, 산출물도 "Spring Boot"(각 단어 독립 후보).
        val result = validator.validate(
            section("Spring Boot로 서버를 만들었다."),
            listOf(source(body = "Spring Boot와 JPA로 개발했다.")),
        )

        // then — "spring"·"boot"가 각각 경계 일치 → 통과.
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 다단어_라틴_이름은_한_단어라도_근거가_없으면_그_단어만_검출된다() {
        // given — 출처엔 "Spring"만 있고 "Boot"는 없다.
        val result = validator.validate(
            section("Spring Batch로 배치를 돌렸다."),
            listOf(source(body = "Spring으로 개발했다.")),
        )

        // then — "Batch"만 근거 없음으로 검출, "Spring"은 통과.
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.text }).containsExactly("Batch")
    }

    @Test
    fun 인용된_다단어_프로젝트명은_경계_포함_부분문자열로_대조한다() {
        // given — 따옴표 인용 프로젝트명(한글 포함)이 출처에 그대로 존재.
        val result = validator.validate(
            section("\"사내 결제 플랫폼\" 프로젝트를 이끌었다."),
            listOf(source(body = "사내 결제 플랫폼 구축을 담당했다.")),
        )

        // then — 인용구가 corpus에 존재 → 통과.
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 인용된_프로젝트명이_출처에_없으면_검출된다() {
        // given — 인용 프로젝트명이 출처 어디에도 없다.
        val result = validator.validate(
            section("\"가상 결제 엔진\" 프로젝트를 이끌었다."),
            listOf(source(body = "사내 결제 플랫폼 구축을 담당했다.")),
        )

        // then — 검출(인용 고유명사 날조 차단).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).contains(FactKind.PROPER_NOUN)
    }

    // ----- [LOW-5] 미근거 PROPER_NOUN의 ungroundedTokens 정밀 단언 -----

    @Test
    fun 근거_없는_고유명사는_정규화_텍스트로_정확히_보고된다() {
        // given — 산출물에 "GraphQL"이 있으나 출처엔 없다.
        val result = validator.validate(
            section("GraphQL로 API를 설계했다."),
            listOf(source(body = "REST로 API를 설계했다.")),
        )

        // then — 검출 토큰은 정확히 "GraphQL" 하나(REST/API는 출처에 있어 근거 있음).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens)
            .extracting<String> { it.text }
            .containsExactlyInAnyOrder("GraphQL")
        assertThat(result.ungroundedTokens.map { it.kind }).containsOnly(FactKind.PROPER_NOUN)
    }

    // ----- [AI-07] NUMERIC 단위 인지 대조 -----

    @Test
    fun 같은_값이라도_단위가_충돌하면_근거없음으로_검출된다() {
        // given (AI-07) — 산출물 "40%"(퍼센트) vs 출처 "40명"(인원). 값 40은 같지만 단위가 다르다.
        val result = validator.validate(
            section("만족도를 40% 높였다."),
            listOf(source(body = "팀원 40명과 협업했다.")),
        )

        // then — 단위 충돌은 날조 누수이므로 근거 없음으로 검출한다(과거 오음성 통과를 차단).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).containsOnly(FactKind.NUMERIC)
        assertThat(result.ungroundedTokens.map { it.text }).contains("40")
    }

    @Test
    fun 같은_값_같은_단위는_통과한다() {
        // given — "40명" 산출물·출처가 값·단위 모두 일치.
        val result = validator.validate(
            section("팀원 40명을 이끌었다."),
            listOf(source(body = "팀원 40명과 협업했다.")),
        )

        // then
        assertThat(result.valid).isTrue()
    }

    @Test
    fun 단위_없는_순수_수량_수사는_대조에서_제외된다() {
        // given (AI-07) — 출처엔 "여러"(숫자 없음), 산출물엔 "3개"(흔한 단위 밖 수량사). 수사적 수치라 검증 제외.
        val result = validator.validate(
            section("핵심 기능 3개를 만들었다."),
            listOf(source(body = "여러 핵심 기능을 만들었다.")),
        )

        // then — "3"이 출처에 없어도 단위 없는 수사적 수치라 드롭하지 않고 통과(거짓 양성 제거).
        assertThat(result.valid).isTrue()
        assertThat(result.ungroundedTokens).isEmpty()
    }

    @Test
    fun 산출물_단위_수치가_출처에_단위없이_있으면_통과한다() {
        // given — 산출물 "40%" vs 출처 "40"(단위 없는 순수 값). 단위 충돌이 아니므로 통과(스케일 동치 유지).
        val result = validator.validate(
            section("만족도를 40% 높였다."),
            listOf(source(body = "지표 40을 기록했다.")),
        )

        // then
        assertThat(result.valid).isTrue()
    }

    // ----- [LOW-1] 빈 출처(층위1 누락) fail-safe -----

    @Test
    fun 출처가_비면_모든_토큰이_근거없음으로_검출된다() {
        // given — relevant가 비도록 항목 출처와 무관한 경험만 제공(코퍼스 "").
        val unrelated = ExperienceRecordId(UUID.randomUUID())
        val result = validator.validate(
            section("Kotlin으로 40% 개선했다.", sources = listOf(expId)),
            listOf(source(body = "Kotlin으로 40% 개선했다.", id = unrelated)),
        )

        // then — 대조할 코퍼스가 없어 수치·고유명사 모두 검출 → VALIDATION_FAILED(fail-safe).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind })
            .contains(FactKind.NUMERIC, FactKind.PROPER_NOUN)
    }

    // ----- [LOW-4] 혼합 토큰: 근거 있는·없는 수치 공존 -----

    @Test
    fun 한_본문에_근거_있는_수치와_없는_수치가_섞이면_없는_것만_보고된다() {
        // given — content에 "30"(출처에 있음)·"99"(출처에 없음) 공존(라틴 단위 없이 순수 수치만).
        val result = validator.validate(
            section("처리량을 30건에서 99건으로 늘렸다."),
            listOf(source(body = "처리량을 30건 처리했다.")),
        )

        // then — valid=false이고 검출 토큰은 근거 없는 "99"만(30은 보고되지 않음).
        assertThat(result.valid).isFalse()
        assertThat(result.ungroundedTokens.map { it.kind }).containsOnly(FactKind.NUMERIC)
        assertThat(result.ungroundedTokens)
            .extracting<String> { it.text }
            .containsExactly("99")
    }
}
