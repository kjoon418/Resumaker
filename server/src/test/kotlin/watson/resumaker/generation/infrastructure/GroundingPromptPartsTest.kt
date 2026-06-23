package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [GroundingPromptParts] 단위 테스트. 1차 생성 어댑터와 품질 개선 처치 어댑터가 공유하는 신뢰성 절대 규칙·출력
 * 스키마가 그대로 유지되는지(추출 리팩터로 인한 회귀 방지) 고정한다.
 *
 * **.cmd 개행 truncation 회귀(ccf323e):** OUTPUT_SCHEMA는 [ClaudeCliClient]가 한 줄로 펴 넘기지만, 스키마 자체가
 * 정상 JSON임을 확인해 한 줄로 펴도 유효 JSON으로 남는지 검증한다(Windows cmd.exe 인자 truncation 우회의 전제).
 */
class GroundingPromptPartsTest {

    @Test
    fun 신뢰성_절대_규칙_블록에_날조_금지와_근거_산출_명령이_포함된다() {
        // given
        val sb = StringBuilder()

        // when
        GroundingPromptParts.appendAbsoluteRules(sb)

        // then — 두 어댑터가 공유해야 할 핵심 가드레일 문구.
        val prompt = sb.toString()
        assertThat(prompt).contains("절대 규칙(신뢰성 가드레일 — 위반 금지)")
        assertThat(prompt).contains("지어내지 마세요")
        assertThat(prompt).contains("sourceExperienceIds")
        assertThat(prompt).contains("factGroundings")
    }

    @Test
    fun 출력_스키마는_유효한_JSON이며_한_줄로_펴도_유효하다() {
        // given (Windows .cmd 개행 truncation 회피의 전제) — 개행을 공백으로 펴도 의미가 보존되는 유효 JSON이어야 한다.
        val mapper = ObjectMapper()
        val singleLine = GroundingPromptParts.OUTPUT_SCHEMA.replace("\n", " ")

        // when and then — 파싱에 성공하고 sections/factGroundings 구조가 그대로 있다.
        val node = mapper.readTree(singleLine)
        val sectionItem = node.path("properties").path("sections").path("items").path("properties")
        assertThat(sectionItem.has("definitionKey")).isTrue()
        assertThat(sectionItem.has("succeeded")).isTrue()
        assertThat(sectionItem.path("factGroundings").path("items").path("properties").has("token")).isTrue()
    }
}
