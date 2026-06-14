package watson.resumaker.account.presentation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import watson.resumaker.account.application.AccountService
import watson.resumaker.account.application.CurrentUserProvider

/**
 * 계정 API: 회원가입(POST /auth/signup), 계정 삭제(DELETE /me).
 * 로그인 엔드포인트는 이번 태스크에서 생략한다(구현 설계 §12 — 후속).
 */
@RestController
class AccountController(
    private val accountService: AccountService,
    private val accountMapper: AccountMapper,
    private val currentUserProvider: CurrentUserProvider,
) {

    @PostMapping("/auth/signup")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<SignUpResponse> {
        val command = accountMapper.toSignUpCommand(request)
        val response = accountService.signUp(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/me")
    fun deleteAccount(): ResponseEntity<Void> {
        accountService.deleteAccount(currentUserProvider.currentUserId())
        return ResponseEntity.noContent().build()
    }
}
