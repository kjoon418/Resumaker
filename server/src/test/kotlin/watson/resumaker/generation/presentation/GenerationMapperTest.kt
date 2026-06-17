package watson.resumaker.generation.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * [GenerationMapper] 단위 테스트. 원시 문자열 식별자가 VO command로 정확히 변환되는지 검증한다(구현 설계 §8).
 * 양식·목표 로딩(소유 격리)은 Service 책임이므로 매퍼는 식별자 VO만 만든다.
 */
class GenerationMapperTest {

    private val mapper = GenerationMapper()

    @Test
    fun 이력서_요청을_경험_목표_양식_VO_command로_변환한다() {
        // given
        val exp1 = UUID.randomUUID()
        val exp2 = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val templateId = UUID.randomUUID()
        val request = ResumeGenerationRequest(
            experienceIds = listOf(exp1.toString(), exp2.toString()),
            targetId = targetId.toString(),
            templateId = templateId.toString(),
        )

        // when
        val command = mapper.toResumeCommand(request)

        // then — value class는 .value로 대조한다(과거 함정).
        assertThat(command.experienceIds.map { it.value }).containsExactly(exp1, exp2)
        assertThat(command.targetId.value).isEqualTo(targetId)
        // 양식은 이제 선택이지만 지정 시 그대로 VO로 변환된다.
        assertThat(command.templateId?.value).isEqualTo(templateId)
    }

    @Test
    fun 양식이_없으면_command_templateId가_null이다() {
        // given (도메인 이해 §178) — 양식 미지정(null/공백)이면 AI 생성 양식 경로로 진입(templateId null).
        val exp1 = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val request = ResumeGenerationRequest(
            experienceIds = listOf(exp1.toString()),
            targetId = targetId.toString(),
            templateId = null,
        )

        // when
        val command = mapper.toResumeCommand(request)

        // then
        assertThat(command.templateId).isNull()
    }

    @Test
    fun 공백_templateId는_AI_생성_양식_경로로_null_처리된다() {
        // given (MEDIUM-2) — 공백/whitespace templateId도 null로 매핑돼야 한다.
        // 이 가드가 없으면 UUID.fromString("")→IllegalArgumentException(500 회귀) 위험이 있다.
        val exp1 = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val request = ResumeGenerationRequest(
            experienceIds = listOf(exp1.toString()),
            targetId = targetId.toString(),
            templateId = "   ", // whitespace-only
        )

        // when
        val command = mapper.toResumeCommand(request)

        // then — isNotBlank() 가드가 공백을 null로 흡수한다(UUID.fromString 500 방지).
        assertThat(command.templateId).isNull()
    }

    @Test
    fun 포트폴리오_요청을_경험_목표_VO_command로_변환한다() {
        // given
        val exp1 = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val request = PortfolioGenerationRequest(
            experienceIds = listOf(exp1.toString()),
            targetId = targetId.toString(),
        )

        // when
        val command = mapper.toPortfolioCommand(request)

        // then
        assertThat(command.experienceIds.map { it.value }).containsExactly(exp1)
        assertThat(command.targetId.value).isEqualTo(targetId)
    }
}
