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
 * 비동기 생성 작업 상태(서버 `GenerationJobStatus`와 1:1). 직렬화 이름은 서버 enum 상수명과 동일해야 한다.
 * - PENDING: 제출 후 대기.
 * - RUNNING: AI가 작성 중.
 * - SUCCEEDED: 완료(부분 성공 ≥1 섹션 포함). 산출물이 생성되어 [GenerationJobResponse.artifactId]가 채워진다.
 * - FAILED: 처리 중 실패. errorCode/errorMessage로 원인을 표면화한다.
 *
 * PENDING·RUNNING은 "활성" 상태로, 목록 화면이 이 상태가 하나라도 있으면 폴링을 유지한다.
 */
@Serializable
enum class GenerationJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    /** 폴링을 계속해야 하는 활성 상태인지(완료/실패면 더 폴링하지 않는다). */
    val isActive: Boolean get() = this == PENDING || this == RUNNING
}

/**
 * 생성 실패 작업의 '다시 만들기' 분류(서버 `GenerationJobRetryMode`와 1:1). 분류 책임은 서버가 가지며 클라이언트는
 * 이 값만 보고 버튼 문구·동작을 정한다(스스로 실패 코드를 재분류하지 않음).
 * - IN_PLACE    : 일시적 실패. 저장된 입력 그대로 목록에서 바로 다시 만든다(제작 화면 이동 없음).
 * - EDIT_INPUTS : 입력 관련 실패. 입력을 미리 채운 제작 화면으로 이동해 사용자가 고쳐 다시 만든다.
 * - NONE        : 다시 만들기를 제공하지 않는다(한도 초과·활성·성공).
 */
@Serializable
enum class GenerationJobRetryMode {
    IN_PLACE,
    EDIT_INPUTS,
    NONE,
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

/**
 * 개선 소견의 처치 종류(서버 `quality.domain.TreatmentKind`와 1:1).
 * - AUTO_REWRITE  : 사실을 불변으로 둔 채 표현·구조·강조·간결만 다듬는 자동 적용 후보를 생성한다.
 * - SUGGESTION    : 사용자 액션이 필요한 보강 안내. 텍스트를 바꾸지 않고 경험 보강으로 유도한다.
 * - OUT_OF_SCOPE  : 서식·접근성(R1/AP14) 등 MVP 자동 개선 범위 밖. 화면에 노출하지 않는다.
 */
@Serializable
enum class TreatmentKind {
    AUTO_REWRITE,
    SUGGESTION,
    OUT_OF_SCOPE,
}
