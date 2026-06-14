package watson.resumaker.account.presentation

import org.springframework.stereotype.Component
import watson.resumaker.account.application.SignUpCommand
import watson.resumaker.account.domain.Credential
import watson.resumaker.account.domain.UserTimeZone
import watson.resumaker.account.infrastructure.PasswordHasher

/**
 * 컨트롤러 계층의 원시 값 -> VO 변환을 담당한다(아키텍처 가이드).
 * 비밀번호 해싱을 거쳐 Credential VO를 구성한다.
 */
@Component
class AccountMapper(
    private val passwordHasher: PasswordHasher,
) {

    fun toSignUpCommand(request: SignUpRequest): SignUpCommand {
        val email = request.email!!
        val rawPassword = request.password!!

        val credential = Credential.of(email, passwordHasher.hash(rawPassword))
        val timeZone = request.timeZone?.let { UserTimeZone(it) } ?: UserTimeZone.DEFAULT
        return SignUpCommand(credential = credential, timeZone = timeZone)
    }
}
