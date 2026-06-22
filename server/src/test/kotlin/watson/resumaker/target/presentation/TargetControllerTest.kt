package watson.resumaker.target.presentation

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.target.application.TargetService
import watson.resumaker.target.domain.TargetBriefId
import java.util.UUID

/**
 * [TargetController] @WebMvcTest(슬라이스). 작성 전략 재시도 엔드포인트의 202·소유 격리(404)·식별자 검증(400)을 본다.
 * 전역 예외 핸들러가 함께 로드된다.
 */
@WebMvcTest(TargetController::class)
class TargetControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: TargetService

    @MockitoBean
    private lateinit var mapper: TargetMapper

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    private val targetId = UUID.randomUUID().toString()

    @Test
    fun 전략_재시도는_202를_돌려준다() {
        // given
        val userId = UserId(UUID.randomUUID())
        whenever(currentUserProvider.currentUserId()).thenReturn(userId)

        // when and then
        mockMvc.post("/targets/$targetId/strategy/retry")
            .andExpect { status { isAccepted() } }

        verify(service).retryStrategy(userId, TargetBriefId(UUID.fromString(targetId)))
    }

    @Test
    fun 타인_소유_또는_미존재_재시도는_404() {
        // given (소유 격리)
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(ResourceNotFoundException("요청하신 목표 정보를 찾을 수 없어요."))
            .whenever(service).retryStrategy(any(), any())

        // when and then
        mockMvc.post("/targets/$targetId/strategy/retry")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun 잘못된_식별자_형식은_400() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))

        // when and then
        mockMvc.post("/targets/not-a-uuid/strategy/retry")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }
}
