package watson.resumaker.generation.domain

/**
 * 생성 실패 후 '다시 만들기'를 어떻게 제공할지에 대한 분류. **분류 책임은 서버 단일 소유**다 — 클라이언트는 이 값만
 * 보고 버튼 문구·동작을 정하고 실패 코드를 스스로 재분류하지 않는다(중복 분류 금지).
 *
 * 실패 원인이 일시적인지(같은 입력으로 다시 만들면 성공 가능) 입력에 기인하는지(같은 입력으론 또 실패)에 따라
 * 사용자 경험이 갈린다:
 * - [IN_PLACE]    : 일시적 실패. 산출물 목록에서 저장된 입력 그대로 다시 만든다(제작 화면 이동 없음).
 * - [EDIT_INPUTS] : 입력 관련 실패. 같은 입력으론 또 실패하므로 입력을 미리 채운 제작 화면으로 보낸다.
 * - [NONE]        : 다시 만들기를 제공하지 않는다(한도 초과·활성·성공).
 */
enum class GenerationJobRetryMode {
    IN_PLACE,
    EDIT_INPUTS,
    NONE,
}

/**
 * 생성 실패 에러 코드 상수([GenerationJobWorker]가 markFailed에 쓰는 코드와 1:1). 워커(코드 생성)와
 * [GenerationJob.retryMode](코드 분류)·테스트가 한 곳을 공유해 매직 문자열·분류 불일치를 막는다.
 */
object GenerationErrorCode {
    /** 외부 AI 일시 불가(API 키 없음·CLI 비정상 종료·파싱 오류·타임아웃 회수). 같은 입력 재시도로 성공 가능. */
    const val AI_UNAVAILABLE = "AI_GENERATION_UNAVAILABLE"

    /** 전 항목 실패 등으로 만들 산출물이 없음. 입력(경험 선택)을 바꿔야 한다. */
    const val NO_CONTENT = "GENERATION_NO_CONTENT"

    /** 제출 후 경험·목표가 삭제돼 생성 재료를 적재하지 못함. 입력을 다시 골라야 한다. */
    const val SOURCE_MISSING = "GENERATION_SOURCE_MISSING"

    /** 일일 한도 초과. 재시도해도 같은 결과라 다시 만들기를 제공하지 않는다. */
    const val QUOTA_EXCEEDED = "GENERATION_QUOTA_EXCEEDED"

    /** 그 외 예기치 못한 실패(일시적으로 간주). */
    const val GENERATION_FAILED = "GENERATION_FAILED"
}
