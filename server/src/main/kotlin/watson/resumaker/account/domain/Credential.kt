package watson.resumaker.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import watson.resumaker.common.domain.DomainValidationException

/**
 * 인증 수단(이메일 + 비밀번호 해시). 다중 필드 VO이므로 @Embeddable.
 *
 * 비밀번호는 반드시 해시 형태로만 보관한다. 원문 비밀번호의 형식 검증·해싱은 인프라(PasswordHasher)의 책임이며,
 * 이 VO는 "이메일 형식이 올바르고, 해시가 비어 있지 않다"는 불변식만 강제한다.
 *
 * MVP 인증 수단은 이메일+비밀번호로 단순화한다(구현 설계 §12). 실제 로그인/JWT는 후속.
 */
@Embeddable
class Credential private constructor(
    @Column(name = "email", nullable = false, unique = true)
    val email: String,
    @Column(name = "password_hash", nullable = false)
    val passwordHash: String,
) {

    init {
        if (!EMAIL_PATTERN.matches(email)) {
            throw DomainValidationException("이메일 형식이 올바르지 않아요. 예: name@example.com")
        }
        if (passwordHash.isBlank()) {
            throw DomainValidationException("비밀번호 정보가 비어 있어요. 다시 시도해 주세요.")
        }
    }

    companion object {
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /**
         * 이미 해시된 비밀번호로 인증 수단을 만든다.
         */
        fun of(email: String, passwordHash: String): Credential =
            Credential(email.trim().lowercase(), passwordHash)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Credential) return false
        return email == other.email && passwordHash == other.passwordHash
    }

    override fun hashCode(): Int = 31 * email.hashCode() + passwordHash.hashCode()
}
