package watson.resumaker.common.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.application.ExperienceService
import watson.resumaker.experience.presentation.ExperienceController
import watson.resumaker.experience.presentation.ExperienceMapper
import java.util.UUID

/**
 * [GlobalExceptionHandler] advice 체인 우선순위 회귀 테스트(MEDIUM-2).
 *
 * 핸들러를 직접 호출하는 단위 테스트와 달리, @WebMvcTest + @RestControllerAdvice 체인을 실제로 경유해
 * 다음을 고정한다:
 * 1. 최후의 [Exception] 폴백이 도메인 예외([DomainValidationException]·[ResourceNotFoundException])를
 *    500으로 삼키지 않고 각 도메인 핸들러가 의도한 상태를 돌려줌.
 * 2. malformed JSON([HttpMessageNotReadableException])이 400 + 친화 envelope로 매핑됨.
 * 3. 잘못된 형식의 경로 식별자(컨트롤러 toId()가 DomainValidationException으로 감쌈)가 400으로 매핑됨.
 */
@WebMvcTest(ExperienceController::class)
class GlobalExceptionHandlerAdviceChainTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var experienceService: ExperienceService

    @MockitoBean
    private lateinit var experienceMapper: ExperienceMapper

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    @Test
    fun DomainValidationException은_Exception_폴백이_아닌_400_핸들러가_처리한다() {
        // given (MEDIUM-2) — 서비스가 DomainValidationException을 던지는 경로.
        // Exception 폴백(500)이 삼키면 이 테스트가 실패해 회귀를 잡는다.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(experienceService.getAll(any()))
            .thenThrow(DomainValidationException("도메인 불변식 위반"))

        // when and then
        mockMvc.get("/experiences")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun ResourceNotFoundException은_Exception_폴백이_아닌_404_핸들러가_처리한다() {
        // given (MEDIUM-2) — 서비스가 ResourceNotFoundException을 던지는 경로.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(experienceService.getAll(any()))
            .thenThrow(ResourceNotFoundException("찾을 수 없어요"))

        // when and then
        mockMvc.get("/experiences")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun malformed_JSON은_400_친화_envelope로_매핑된다() {
        // given (MEDIUM-2, D2) — 잘못된 JSON 본문으로 HttpMessageNotReadableException 발생.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))

        // when and then
        mockMvc.post("/experiences") {
            contentType = MediaType.APPLICATION_JSON
            content = "{ invalid json }"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.message") { isNotEmpty() }
        }
    }

    @Test
    fun 잘못된_형식의_경로_식별자는_400_친화_envelope로_매핑된다() {
        // given (D1, MEDIUM-2) — toId()가 UUID.fromString 실패를 DomainValidationException으로 감싼다.
        // Exception 폴백(500)이 아니라 DomainValidationException 핸들러(400)가 처리함을 Spring 체인으로 검증한다.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))

        // when and then
        mockMvc.get("/experiences/not-a-uuid")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }
}
