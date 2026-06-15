package watson.resumaker.template.domain

/**
 * 섹션 성격(도메인 이해 §2.5 / §산출물 구조와 상태 모델 "생성 항목의 종류").
 *
 * 생성 시 섹션 정의 하나가 산출물의 생성 항목으로 실체화될 때의 카디널리티를 규정한다.
 * - SUMMARY(요약형): 여러 경험을 종합해 만들어지며 특정 경험에 매이지 않는다(경험과 N:M).
 * - CAREER(경력형): 하나 이상의 경험에 근거한다.
 *
 * 명세 §304 항목 2종과 1:1 대응한다. enum 상수명은 클라이언트와 1:1로 직렬화되므로 그대로 유지한다.
 */
enum class SectionCharacter(val label: String) {
    SUMMARY("요약형"),
    CAREER("경력형"),
}
