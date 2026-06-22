package watson.resumaker.target.domain

/**
 * AI 작성 전략 VO(목표 내부 파생값, 채용 방향에서 추출).
 *
 * "이 회사에 맞춰 이력서/포트폴리오를 어떻게 작성할지"의 구조화된 관점이다. 채용 방향 원문(RecruitDirection)에서
 * LLM이 비동기로 추출해 목표에 저장하고, 이력서·포트폴리오 생성 시 원문 대신 이 전략을 프롬프트에 쓴다(없으면 원문 폴백).
 *
 * 별도 애그리거트가 아니라 Target 내부 파생값이므로 plain data class로 둔다. JSON 직렬화/역직렬화는
 * application/presentation 계층에서 Jackson으로 수행하고, 엔티티([TargetBrief])는 JSON 문자열만 보관한다
 * (JPA 컨버터/@ElementCollection 복잡성 회피, @Modifying 조건부 쓰기와 양립).
 *
 * @param keywords  강조할 핵심 역량(키워드 목록).
 * @param tone      권장 어조.
 * @param emphasize 강조할 점.
 * @param avoid     피할 점.
 * @param summary   작성 관점에서 압축한 공고 요약(작성과 무관한 복리후생·접수방법·근무지 등은 제외).
 */
data class WritingStrategy(
    val keywords: List<String>,
    val tone: String,
    val emphasize: List<String>,
    val avoid: List<String>,
    val summary: String,
)

/**
 * 작성 전략 추출 상태. 목표 저장 직후 PENDING → 워커가 클레임하면 EXTRACTING → 종료 시 READY|FAILED.
 *
 * 채용 방향이 바뀌는 수정이 일어나면 다시 PENDING으로 돌아가 자동 재추출된다. 생성 통합은 READY일 때만
 * 전략을 프롬프트에 싣는다(그 외에는 원문 폴백).
 */
enum class StrategyStatus {
    PENDING,
    EXTRACTING,
    READY,
    FAILED,
}
