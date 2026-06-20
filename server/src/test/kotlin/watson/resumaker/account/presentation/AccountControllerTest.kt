package watson.resumaker.account.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.AccountService
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.auth.application.IssuedTokens
import watson.resumaker.auth.application.TokenService
import watson.resumaker.auth.presentation.AuthCookieService
import java.time.Duration

@WebMvcTest(AccountController::class)
class AccountControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var accountService: AccountService

    @MockitoBean
    private lateinit var accountMapper: AccountMapper

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    @MockitoBean
    private lateinit var tokenService: TokenService

    @MockitoBean
    private lateinit var cookieService: AuthCookieService

    @Test
    fun 로그인_요청이_성공하면_200과_userId를_반환한다() {
        // given
        whenever(accountService.login(any(), any())).thenReturn(LoginResponse(USER_ID))
        whenever(tokenService.issue(any())).thenReturn(
            IssuedTokens("access", "refresh", Duration.ofMinutes(30), Duration.ofDays(14)),
        )
        val request = LoginRequest(email = EMAIL, password = PASSWORD)

        // when and then
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(USER_ID) }
        }
    }

    @Test
    fun 이메일이_누락되면_4XX를_반환한다() {
        // given
        val request = LoginRequest(email = null, password = PASSWORD)

        // when and then
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun 비밀번호가_누락되면_4XX를_반환한다() {
        // given
        val request = LoginRequest(email = EMAIL, password = null)

        // when and then
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun 가입시_비밀번호가_8자_미만이면_4XX를_반환한다() {
        // given (D3) — 최소 길이 정책 위반.
        val request = SignUpRequest(email = EMAIL, password = SHORT_PASSWORD)

        // when and then
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    companion object {
        private const val EMAIL = "user@example.com"
        private const val PASSWORD = "password1"
        private const val SHORT_PASSWORD = "1"
        private const val USER_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
