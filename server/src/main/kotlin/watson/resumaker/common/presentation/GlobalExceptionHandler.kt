package watson.resumaker.common.presentation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.common.domain.UnauthorizedException

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
     * 인증 주체 해석 실패 → 401.
     */
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(exception: UnauthorizedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(code = "UNAUTHORIZED", message = exception.message ?: "로그인 정보가 필요해요. 다시 로그인해 주세요."))
}
