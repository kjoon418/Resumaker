package watson.resumaker.model.type

import kotlinx.serialization.Serializable

/**
 * 섹션 성격(백엔드 enum과 1:1, 도메인 이해 §2.5).
 * 직렬화 이름은 서버 enum 상수명과 동일해야 한다(JSON 정합).
 *
 * - SUMMARY(요약형): 여러 경험을 종합(경험과 N:M).
 * - CAREER(경력형): 하나 이상의 경험에 근거.
 */
@Serializable
enum class SectionCharacter(val label: String) {
    SUMMARY("요약형"),
    CAREER("경력형"),
}
