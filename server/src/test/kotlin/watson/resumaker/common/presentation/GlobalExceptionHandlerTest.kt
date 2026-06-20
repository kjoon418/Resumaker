package watson.resumaker.common.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.EmptyExperienceSelectionException
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.common.domain.UnauthorizedException
import java.util.UUID

/**
 * [GlobalExceptionHandler] 단위 테스트. 도메인 예외 → HTTP 상태·응답 코드·action 매핑을 검증한다.
 * Spring 컨텍스트 없이 핸들러를 직접 생성해 빠르게 확인한다.
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun QuotaExceededException은_429로_매핑되고_code와_action이_응답에_포함된다() {
        // given (수용 기준 15, 도메인 이해 §399) — 1차 생성 한도 초과.
        val exception = QuotaExceededException(
            message = "오늘 만들 수 있는 이력서·포트폴리오 횟수를 모두 썼어요. 내일 다시 이어서 만들 수 있어요.",
            code = "GENERATION_QUOTA_EXCEEDED",
            action = "EDIT_MANUALLY",
        )

        // when
        val response = handler.handleQuotaExceeded(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.body!!.code).isEqualTo("GENERATION_QUOTA_EXCEEDED")
        assertThat(response.body!!.action).isEqualTo("EDIT_MANUALLY")
        assertThat(response.body!!.message).contains("이력서·포트폴리오")
    }

    @Test
    fun 재생성_한도_초과도_429로_매핑된다() {
        // given — 항목 재생성 한도 초과.
        val exception = QuotaExceededException(
            message = "이 항목을 오늘 다시 만들 수 있는 횟수를 모두 썼어요. 내일 다시 시도해 주세요.",
            code = "REGENERATION_QUOTA_EXCEEDED",
            action = "EDIT_MANUALLY",
        )

        // when
        val response = handler.handleQuotaExceeded(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.body!!.code).isEqualTo("REGENERATION_QUOTA_EXCEEDED")
        assertThat(response.body!!.action).isEqualTo("EDIT_MANUALLY")
    }

    @Test
    fun DomainValidationException은_400으로_매핑된다() {
        val response = handler.handleDomainValidation(DomainValidationException("잘못된 입력"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.code).isEqualTo("INVALID_REQUEST")
    }

    @Test
    fun ResourceNotFoundException은_404로_매핑된다() {
        val response = handler.handleNotFound(ResourceNotFoundException("찾을 수 없어요"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body!!.code).isEqualTo("NOT_FOUND")
    }

    @Test
    fun EmptyExperienceSelectionException은_409로_매핑되고_ADD_EXPERIENCE_action이_포함된다() {
        val response = handler.handleEmptyExperienceSelection(EmptyExperienceSelectionException("경험을 선택해 주세요"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body!!.code).isEqualTo("EMPTY_EXPERIENCE_SELECTION")
        assertThat(response.body!!.action).isEqualTo("ADD_EXPERIENCE")
    }

    @Test
    fun ConflictException은_409로_매핑되고_action이_전달된다() {
        val response = handler.handleConflict(ConflictException("진행 중", action = "RETRY_LATER"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body!!.code).isEqualTo("CONFLICT")
        assertThat(response.body!!.action).isEqualTo("RETRY_LATER")
    }

    @Test
    fun UnauthorizedException은_401로_매핑된다() {
        val response = handler.handleUnauthorized(UnauthorizedException("로그인 필요"))
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(response.body!!.code).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun 잘못된_형식의_경로_식별자는_DomainValidationException을_통해_400으로_매핑된다() {
        // given (D1) — 컨트롤러 toId()가 UUID 파싱 실패 시 DomainValidationException으로 감싸 던진다.
        // 서비스 계층 requireNotNull(IllegalArgumentException)과 달리 클라이언트 입력 오류(400)로 분류된다.
        val response = handler.handleDomainValidation(DomainValidationException("입력 형식을 다시 확인해 주세요."))

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.code).isEqualTo("INVALID_REQUEST")
        assertThat(response.body!!.message).isEqualTo("입력 형식을 다시 확인해 주세요.")
    }

    @Test
    fun 경로_변수_타입_불일치는_400_친화_envelope로_매핑된다() {
        // given (D1) — @PathVariable UUID 바인딩 실패.
        val exception = MethodArgumentTypeMismatchException("not-a-uuid", UUID::class.java, "id", mock<MethodParameter>(), null)

        // when
        val response = handler.handleTypeMismatch(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.code).isEqualTo("INVALID_REQUEST")
        assertThat(response.body!!.message).isEqualTo("입력 형식을 다시 확인해 주세요.")
    }

    @Test
    fun 역직렬화_실패는_400_친화_envelope로_매핑된다() {
        // given (D2) — malformed JSON·잘못된 enum 값.
        val exception = HttpMessageNotReadableException("malformed", MockHttpInputMessage(ByteArray(0)))

        // when
        val response = handler.handleNotReadable(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body!!.code).isEqualTo("INVALID_REQUEST")
        assertThat(response.body!!.message).isNotBlank()
    }

    @Test
    fun 미처리_예외는_500_INTERNAL_ERROR_envelope로_매핑되고_내부정보를_노출하지_않는다() {
        // given (D2) — 폴백으로 잡혀야 하는 임의의 미처리 예외.
        val internalDetail = "NullPointerException at SomeService.kt:42"

        // when
        val response = handler.handleUnexpected(RuntimeException(internalDetail))

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body!!.code).isEqualTo("INTERNAL_ERROR")
        assertThat(response.body!!.message).doesNotContain(internalDetail)
    }
}
