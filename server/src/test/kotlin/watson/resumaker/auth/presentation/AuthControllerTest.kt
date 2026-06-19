package watson.resumaker.auth.presentation

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import watson.resumaker.auth.application.IssuedTokens
import watson.resumaker.auth.application.TokenService
import java.time.Duration

/**
 * [AuthController] @WebMvcTest. 토큰 서비스·쿠키 서비스를 모킹한다(슬라이스). CSRF 필터는 슬라이스에 실리지 않으므로
 * 커스텀 헤더 없이도 호출된다(필터는 풀 컨텍스트에서만 적용 — [SecurityFilterConfig]).
 */
@WebMvcTest(AuthController::class)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var tokenService: TokenService

    @MockitoBean
    private lateinit var cookieService: AuthCookieService

    @Test
    fun refresh는_유효한_refresh쿠키면_204로_토큰을_회전한다() {
        whenever(cookieService.readRefresh(any())).thenReturn("refresh-token")
        whenever(tokenService.refresh("refresh-token")).thenReturn(
            IssuedTokens("new-access", "new-refresh", Duration.ofMinutes(30), Duration.ofDays(14)),
        )

        mockMvc.post("/auth/refresh").andExpect { status { isNoContent() } }
    }

    @Test
    fun refresh쿠키가_없으면_401을_반환한다() {
        whenever(cookieService.readRefresh(any())).thenReturn(null)

        mockMvc.post("/auth/refresh").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun refresh토큰이_무효하면_401을_반환한다() {
        whenever(cookieService.readRefresh(any())).thenReturn("stale")
        whenever(tokenService.refresh("stale")).thenReturn(null)

        mockMvc.post("/auth/refresh").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun logout은_토큰을_폐기하고_204를_반환한다() {
        whenever(cookieService.readAccess(any())).thenReturn("access-token")
        whenever(cookieService.readRefresh(any())).thenReturn("refresh-token")

        mockMvc.post("/auth/logout").andExpect { status { isNoContent() } }
    }
}
