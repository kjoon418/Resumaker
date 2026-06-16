package watson.resumaker.template.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.presentation.InterpretResponse

class TemplateInterpretServiceTest {

    private val interpreter: ResumeTemplateInterpreter = mock()
    private val service = TemplateInterpretService(interpreter)

    @Test
    fun Interpreted_결과는_후보_섹션을_반환한다() {
        // given — fake interpreter가 섹션 2개를 추출했다고 가정한다.
        val sections = listOf(
            SectionDefinition.of("자기소개", SectionCharacter.SUMMARY, required = true),
            SectionDefinition.of("주요 경력", SectionCharacter.CAREER, required = false),
        )
        whenever(interpreter.interpret("회사 양식 텍스트")).thenReturn(
            TemplateInterpretation.Interpreted(sections),
        )

        // when
        val response = service.interpret("회사 양식 텍스트")

        // then
        assertThat(response.status).isEqualTo(InterpretResponse.STATUS_INTERPRETED)
        assertThat(response.sections).hasSize(2)
        assertThat(response.sections[0].name).isEqualTo("자기소개")
        assertThat(response.sections[1].name).isEqualTo("주요 경력")
    }

    @Test
    fun Unavailable_결과는_폴백_신호를_반환한다() {
        // given — 기본 어댑터(UnavailableResumeTemplateInterpreter)와 동일한 동작.
        whenever(interpreter.interpret("알 수 없는 텍스트")).thenReturn(
            TemplateInterpretation.Unavailable,
        )

        // when
        val response = service.interpret("알 수 없는 텍스트")

        // then
        assertThat(response.status).isEqualTo(InterpretResponse.STATUS_UNAVAILABLE)
        assertThat(response.sections).isEmpty()
    }
}
