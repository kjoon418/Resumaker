package watson.resumaker.account.presentation

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.AccountService
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.auth.application.TokenService
import watson.resumaker.auth.presentation.AuthCookieService
import java.util.UUID

/**
 * 계정 API: 회원가입(POST /auth/signup), 로그인(POST /auth/login), 계정 삭제(DELETE /me).
 *
 * 로그인·가입 성공 시 불투명 access/refresh 토큰을 발급해 **HttpOnly 쿠키**로 내려보낸다([AuthCookieService]).
 * 응답 본문의 userId는 표시·호환용이며 더 이상 인증 자격증명이 아니다(자격증명은 쿠키). 계정 삭제 시에는
 * 데이터 삭제 후 토큰을 폐기하고 쿠키를 비운다(자동 로그아웃).
 */
@RestController
class AccountController(
    private val accountService: AccountService,
    private val accountMapper: AccountMapper,
    private val currentUserProvider: CurrentUserProvider,
    private val tokenService: TokenService,
    private val cookieService: AuthCookieService,
) {

    @PostMapping("/auth/signup")
    fun signUp(
        @Valid @RequestBody request: SignUpRequest,
        response: HttpServletResponse,
    ): ResponseEntity<SignUpResponse> {
        val command = accountMapper.toSignUpCommand(request)
        val result = accountService.signUp(command)
        issueTokens(result.userId, response)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PostMapping("/auth/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): ResponseEntity<LoginResponse> {
        val result = accountService.login(request.email!!, request.password!!)
        issueTokens(result.userId, response)
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/me")
    fun deleteAccount(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        accountService.deleteAccount(currentUserProvider.currentUserId())
        // 삭제된 계정의 세션을 즉시 무효화한다(쿠키 폐기 + Redis 토큰 삭제).
        tokenService.revoke(cookieService.readAccess(request), cookieService.readRefresh(request))
        cookieService.clear(response)
        return ResponseEntity.noContent().build()
    }

    /** 발급된 userId로 토큰쌍을 만들어 HttpOnly 쿠키로 응답에 싣는다. */
    private fun issueTokens(userId: String, response: HttpServletResponse) {
        val issued = tokenService.issue(UserId(UUID.fromString(userId)))
        cookieService.attach(response, issued)
    }
}
