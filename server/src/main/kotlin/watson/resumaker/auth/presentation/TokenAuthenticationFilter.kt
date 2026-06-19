package watson.resumaker.auth.presentation

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import watson.resumaker.auth.application.TokenService

/**
 * access 쿠키를 읽어 유효하면 현재 사용자(UserId)를 요청 속성에 채운다. **요청을 거부하지는 않는다** —
 * 인증이 필요한 컨트롤러가 [watson.resumaker.account.application.CurrentUserProvider]로 사용자를 요구할 때
 * 비로소 401이 발생한다(공개 엔드포인트는 사용자 없이 동작). 기존 X-User-Id 헤더 기반 provider를 대체하는 배선이다.
 *
 * 비용: 유효한 access 쿠키가 있을 때만 [TokenService.authenticate](Redis O(1) 조회) 1회. 쿠키가 없으면 조회도 없다.
 */
class TokenAuthenticationFilter(
    private val tokenService: TokenService,
    private val cookieService: AuthCookieService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        cookieService.readAccess(request)?.let { accessToken ->
            tokenService.authenticate(accessToken)?.let { userId ->
                request.setAttribute(AUTH_USER_ATTRIBUTE, userId)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        /** 인증된 UserId를 담는 요청 속성 키([RequestAttributeCurrentUserProvider]가 읽는다). */
        const val AUTH_USER_ATTRIBUTE = "resumaker.auth.userId"
    }
}
