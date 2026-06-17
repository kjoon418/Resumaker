package watson.resumaker.model.type

import kotlinx.serialization.Serializable

/**
 * 산출물 종류(서버 `artifact.domain.ArtifactKind`와 1:1).
 * - RESUME(이력서): 지정 양식 기반. 항목은 SUMMARY|CAREER.
 * - PORTFOLIO(포트폴리오): 양식 없음. 항목은 EXPERIENCE_NARRATIVE(경험과 1:1).
 *
 * 직렬화 이름은 서버 enum 상수명과 동일해야 한다(JSON 정합).
 */
@Serializable
enum class ArtifactKind {
    RESUME,
    PORTFOLIO,
}

/**
 * 생성 항목의 종류(서버 `artifact.domain.SectionKind`와 1:1).
 * - 이력서: SUMMARY(요약형), CAREER(경력형).
 * - 포트폴리오: EXPERIENCE_NARRATIVE(선택 경험과 1:1).
 */
@Serializable
enum class SectionKind {
    SUMMARY,
    CAREER,
    EXPERIENCE_NARRATIVE,
}

/**
 * 생성 항목 상태(서버 `artifact.domain.SectionStatus`와 1:1).
 * 부분 실패 버전도 정상 응답(200)으로 내려오며, 항목별 상태로 성공/실패를 구분한다(도메인 이해 §306).
 * - GENERATED: 정상 생성.
 * - GENERATION_FAILED: AI 호출 실패/타임아웃.
 * - VALIDATION_FAILED: 자동 검증 실패.
 *
 * GENERATING은 생성 완료 응답에는 나타나지 않지만 enum 정합을 위해 포함한다.
 */
@Serializable
enum class SectionStatus {
    GENERATING,
    GENERATED,
    GENERATION_FAILED,
    VALIDATION_FAILED,
}

/**
 * 고정밀 사실 근거(층위 2)의 종류(서버 `artifact.domain.FactKind`와 1:1).
 * - NUMERIC: 정량 수치.
 * - PROPER_NOUN: 회사명·기술명·프로젝트명 등 고유명사.
 */
@Serializable
enum class FactKind {
    NUMERIC,
    PROPER_NOUN,
}

/**
 * 1차 생성 결과의 양식 출처 신호(서버 `generation.presentation.TemplateOrigin`와 1:1).
 * [AI_FALLBACK_DEFAULT]이면 화면이 폴백 고지를 표시한다(§187 정직성·가짜 성공 금지).
 */
@Serializable
enum class TemplateOrigin {
    USER_SELECTED,
    AI_GENERATED,
    AI_FALLBACK_DEFAULT,
    NONE,
}
