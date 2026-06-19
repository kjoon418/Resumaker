package watson.resumaker.auth.presentation

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import watson.resumaker.auth.application.IssuedTokens
import watson.resumaker.auth.infrastructure.AuthTokenProperties
import java.time.Duration

/**
 * 인증 토큰을 HttpOnly 쿠키로 굽고/지우고/읽는다(전송 계층).
 *
 * access·refresh 모두 **HttpOnly**(JS 접근 불가 → XSS 토큰 탈취 차단) + **Secure** + **SameSite=None**(cross-site
 * 전송) 쿠키로 내려보낸다. CSRF는 쿠키 토큰이 아니라 커스텀 헤더 요구 + CORS 허용목록으로 막으므로(CsrfFilter),
 * 여기서는 별도 CSRF 쿠키를 두지 않는다.
 */
@Component
class AuthCookieService(
    private val properties: AuthTokenProperties,
) {

    /** 로그인·가입·재발급 성공 시 access/refresh 쿠키를 응답에 싣는다. */
    fun attach(response: HttpServletResponse, issued: IssuedTokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, issued.accessToken, issued.accessTtl))
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, issued.refreshToken, issued.refreshTtl))
    }

    /** 로그아웃·계정삭제 시 두 쿠키를 즉시 만료(maxAge 0)시켜 브라우저에서 제거한다. */
    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", Duration.ZERO))
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", Duration.ZERO))
    }

    fun readAccess(request: HttpServletRequest): String? = read(request, ACCESS_COOKIE)

    fun readRefresh(request: HttpServletRequest): String? = read(request, REFRESH_COOKIE)

    private fun cookie(name: String, value: String, maxAge: Duration): String =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(properties.cookieSecure)
            .sameSite(properties.cookieSameSite)
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()

    private fun read(request: HttpServletRequest, name: String): String? =
        request.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() }

    companion object {
        const val ACCESS_COOKIE = "ACCESS_TOKEN"
        const val REFRESH_COOKIE = "REFRESH_TOKEN"
    }
}
