package watson.resumaker.auth.presentation

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import watson.resumaker.account.domain.UserId
import watson.resumaker.auth.application.InMemoryAuthTokenStore
import watson.resumaker.auth.application.TokenService
import watson.resumaker.auth.infrastructure.AuthTokenProperties
import java.util.UUID

/**
 * [TokenAuthenticationFilter] 단위 테스트. 유효한 access 쿠키가 있으면 현재 사용자를 요청 속성에 채우고,
 * 없거나 무효하면 채우지 않는다(거부는 하지 않음 — CurrentUserProvider가 401 책임).
 */
class TokenAuthenticationFilterTest {

    private val properties = AuthTokenProperties()
    private val tokenService = TokenService(InMemoryAuthTokenStore(), properties)
    private val cookieService = AuthCookieService(properties)
    private val filter = TokenAuthenticationFilter(tokenService, cookieService)

    @Test
    fun 유효한_access쿠키면_요청속성에_사용자를_채운다() {
        val userId = UserId(UUID.randomUUID())
        val issued = tokenService.issue(userId)
        val request = MockHttpServletRequest("GET", "/experiences")
        request.setCookies(Cookie(AuthCookieService.ACCESS_COOKIE, issued.accessToken))

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertEquals(userId, request.getAttribute(TokenAuthenticationFilter.AUTH_USER_ATTRIBUTE))
    }

    @Test
    fun access쿠키가_없으면_사용자를_채우지_않는다() {
        val request = MockHttpServletRequest("GET", "/experiences")

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(request.getAttribute(TokenAuthenticationFilter.AUTH_USER_ATTRIBUTE))
    }

    @Test
    fun 무효한_access쿠키면_사용자를_채우지_않는다() {
        val request = MockHttpServletRequest("GET", "/experiences")
        request.setCookies(Cookie(AuthCookieService.ACCESS_COOKIE, "garbage"))

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(request.getAttribute(TokenAuthenticationFilter.AUTH_USER_ATTRIBUTE))
    }
}
