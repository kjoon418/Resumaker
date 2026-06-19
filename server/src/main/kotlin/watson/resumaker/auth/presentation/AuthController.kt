package watson.resumaker.auth.presentation

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.auth.application.TokenService
import watson.resumaker.common.domain.UnauthorizedException

/**
 * 세션 갱신·종료 API.
 * - POST /auth/refresh: refresh 쿠키로 새 토큰쌍을 발급(회전)하고 쿠키를 갱신한다.
 * - POST /auth/logout: 현재 토큰을 폐기하고 쿠키를 비운다.
 *
 * 로그인·가입(쿠키 발급)은 계정 식별과 묶여 [watson.resumaker.account.presentation.AccountController]에 있다.
 */
@RestController
class AuthController(
    private val tokenService: TokenService,
    private val cookieService: AuthCookieService,
) {

    @PostMapping("/auth/refresh")
    fun refresh(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        val refreshToken = cookieService.readRefresh(request)
            ?: throw UnauthorizedException("로그인 정보가 필요해요. 다시 로그인해 주세요.")
        val issued = tokenService.refresh(refreshToken)
            ?: throw UnauthorizedException("로그인이 만료됐어요. 다시 로그인해 주세요.")
        cookieService.attach(response, issued)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/auth/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        // 토큰이 이미 없거나 만료됐어도 멱등하게 동작한다(쿠키만 비운다).
        tokenService.revoke(cookieService.readAccess(request), cookieService.readRefresh(request))
        cookieService.clear(response)
        return ResponseEntity.noContent().build()
    }
}
