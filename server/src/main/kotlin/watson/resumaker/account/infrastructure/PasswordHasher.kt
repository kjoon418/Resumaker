package watson.resumaker.account.infrastructure

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 원문 비밀번호를 해시로 변환한다. 비밀번호 해싱은 인프라 관심사다(구현 설계 §3.2).
 *
 * 표준 알고리즘(PBKDF2WithHmacSHA256, JDK 내장)을 사용한다. 추가 라이브러리 의존 없이 표준 KDF를 쓰기 위해 BCrypt 대신 PBKDF2를 택했다.
 * 저장 형식: "iterations:base64(salt):base64(hash)".
 */
@Component
class PasswordHasher {

    private val random = SecureRandom()

    fun hash(rawPassword: String): String {
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val hash = pbkdf2(rawPassword, salt, ITERATIONS)
        return "$ITERATIONS:${encode(salt)}:${encode(hash)}"
    }

    fun matches(rawPassword: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 3) return false

        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = decode(parts[1])
        val expected = decode(parts[2])
        val actual = pbkdf2(rawPassword, salt, iterations)
        return expected.contentEquals(actual)
    }

    private fun pbkdf2(rawPassword: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val SALT_LENGTH = 16
        private const val KEY_LENGTH = 256
    }
}
