package watson.resumaker.common.presentation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.EmptyExperienceSelectionException
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.common.domain.UnauthorizedException
import watson.resumaker.generation.infrastructure.ClaudeCliException

/**
 * 도메인 예외와 Bean Validation 실패를 사용자 친화적 에러 응답으로 변환한다(구현 설계 §9, UX 에러 가이드).
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * 필수값 누락(Controller DTO Bean Validation) → 400 + 어느 필드인지 안내.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldError = exception.bindingResult.fieldErrors.firstOrNull()
        val field = fieldError?.field
        val message = fieldError?.defaultMessage ?: "입력값을 다시 확인해 주세요."

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = message, field = field))
    }

    /**
     * 도메인 불변식 위반(VO/엔티티 검증) → 400.
     */
    @ExceptionHandler(DomainValidationException::class)
    fun handleDomainValidation(exception: DomainValidationException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = exception.message ?: "입력값을 다시 확인해 주세요."))

    /**
     * 리소스 없음 / 권한 위반 → 404(존재 노출 최소화).
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(exception: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(code = "NOT_FOUND", message = exception.message ?: "요청하신 정보를 찾을 수 없어요."))

    /**
     * 빈 경험 묶음 생성 시도 → 409 + 경험 추가 유도 action(수용 기준 8, 구현 설계 §9).
     * 입력 형식 오류가 아니라 현재 상태로는 생성할 수 없는 충돌이므로 409로 매핑한다.
     */
    @ExceptionHandler(EmptyExperienceSelectionException::class)
    fun handleEmptyExperienceSelection(exception: EmptyExperienceSelectionException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    code = "EMPTY_EXPERIENCE_SELECTION",
                    message = exception.message ?: "이력서·포트폴리오를 만들려면 경험을 하나 이상 골라 주세요.",
                    action = "ADD_EXPERIENCE",
                ),
            )

    /**
     * 현재 상태와의 충돌(예: 같은 항목 재생성 진행 중 중복 요청) → 409 + 진행 중 안내(수용 기준 20, 구현 설계 §305).
     */
    @ExceptionHandler(ConflictException::class)
    fun handleConflict(exception: ConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(
                ErrorResponse(
                    code = "CONFLICT",
                    message = exception.message ?: "지금은 이 작업을 할 수 없어요. 잠시 후 다시 시도해 주세요.",
                    action = exception.action,
                ),
            )

    /**
     * 비용 가드레일 상한 초과 → 429(Too Many Requests) + 회복 시점·대안 안내(수용 기준 15, 도메인 이해 §399).
     * 사용량 한도 소진은 입력 오류(400)도 상태 충돌(409)도 아니므로, 의미상 정확한 429로 매핑한다.
     * code는 어떤 한도인지(1차 생성/항목 재생성) 구분해 클라이언트 분기를 돕는다.
     */
    @ExceptionHandler(QuotaExceededException::class)
    fun handleQuotaExceeded(exception: QuotaExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(
                ErrorResponse(
                    code = exception.code,
                    message = exception.message ?: "오늘 사용할 수 있는 횟수를 모두 썼어요. 내일 다시 시도해 주세요.",
                    action = exception.action,
                ),
            )

    /**
     * 인증 주체 해석 실패 → 401.
     */
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(exception: UnauthorizedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(code = "UNAUTHORIZED", message = exception.message ?: "로그인 정보가 필요해요. 다시 로그인해 주세요."))

    /**
     * 경로 변수 타입 불일치(@PathVariable이 기대하는 타입과 다른 값) → 400(D1).
     * 클라이언트의 잘못된 입력 형식을 서버 오류(500)가 아닌 입력 오류로 안내한다.
     * 내부 서버 장애(requireNotNull 등)는 이 핸들러가 아닌 최후 Exception 폴백(500)이 처리한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = "입력 형식을 다시 확인해 주세요."))

    /**
     * 요청 본문 역직렬화 실패(malformed JSON·잘못된 enum 값) → 400(D2).
     * 기본 Spring envelope 대신 공통 envelope로 내려, 프론트가 code 기반으로 파싱할 수 있게 한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(exception: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(code = "INVALID_REQUEST", message = "입력 형식을 다시 확인해 주세요."))

    /**
     * Claude CLI 호출·파싱 실패(API 키 없음·CLI 비정상 종료·응답 파싱 오류 등) → 503.
     * 외부 AI 서비스 일시 불가를 500(내부 서버 오류)과 구분해 클라이언트가 재시도 여부를 판단할 수 있게 한다.
     * (구현 설계 §12, UX 에러 가이드 — 회복 경로 제시)
     */
    @ExceptionHandler(ClaudeCliException::class)
    fun handleClaudeCliException(exception: ClaudeCliException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                ErrorResponse(
                    code = "AI_GENERATION_UNAVAILABLE",
                    message = "지금은 AI 생성을 사용할 수 없어요. 잠시 후 다시 시도해 주세요.",
                    action = "RETRY_LATER",
                ),
            )

    /**
     * 미처리 예외 최후 폴백 → 500(D2). 내부 정보(스택·메시지)를 노출하지 않고 공통 envelope로 안내한다.
     * 모든 에러가 code+message를 갖도록 보장해, 프론트의 non-null code 파싱이 깨지지 않게 한다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(code = "INTERNAL_ERROR", message = "일시적인 문제가 발생했어요. 잠시 후 다시 시도해 주세요."))
}
