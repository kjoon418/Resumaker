package watson.resumaker.target.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.WritingStrategy
import watson.resumaker.target.presentation.TargetResponse
import watson.resumaker.target.presentation.WritingStrategyResponse

/**
 * 서비스 계층의 Response DTO 변환을 담당한다(Service Mapper).
 *
 * 작성 전략은 엔티티가 JSON 문자열([TargetBrief.writingStrategyJson])로만 들고 있으므로, 여기서 Jackson으로
 * 구조화 DTO로 역직렬화한다. JSON이 비어 있거나 깨졌으면 전략은 null로 두되 상태([TargetBrief.strategyStatus])는
 * 그대로 전달한다(클라이언트가 상태로 폴링·표시).
 */
@Component
class TargetServiceMapper(
    private val objectMapper: ObjectMapper,
) {

    fun toResponse(brief: TargetBrief): TargetResponse =
        TargetResponse(
            id = brief.id.value.toString(),
            recruitDirection = brief.recruitDirection.value,
            companyName = brief.company?.value,
            jobTitle = brief.job?.value,
            writingStrategy = brief.writingStrategyJson?.let { toWritingStrategy(it) },
            strategyStatus = brief.strategyStatus,
        )

    fun toResponses(briefs: List<TargetBrief>): List<TargetResponse> =
        briefs.map { toResponse(it) }

    /** JSON 문자열 → 구조화 DTO. 깨진 JSON은 null로 관용 처리(상태는 별도 필드가 유지). */
    private fun toWritingStrategy(json: String): WritingStrategyResponse? =
        runCatching { objectMapper.readValue<WritingStrategy>(json) }.getOrNull()?.let {
            WritingStrategyResponse(
                keywords = it.keywords,
                tone = it.tone,
                emphasize = it.emphasize,
                avoid = it.avoid,
                summary = it.summary,
            )
        }
}
