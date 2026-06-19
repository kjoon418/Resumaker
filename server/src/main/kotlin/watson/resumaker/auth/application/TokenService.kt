package watson.resumaker.auth.application

import org.springframework.stereotype.Service
import watson.resumaker.account.domain.UserId
import watson.resumaker.auth.infrastructure.AuthTokenProperties
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 불투명 인증 토큰 유스케이스. access/refresh 토큰을 발급·검증·회전·폐기한다(구현 설계 §12 인증 대체).
 *
 * **토큰 생성:** [SecureRandom] 32바이트를 base64url로 인코딩한 예측 불가 문자열. 의미를 담지 않는다(참조 토큰).
 * **저장:** 원문이 아니라 SHA-256 해시만 [AuthTokenStore]에 저장한다(저장소 유출 시에도 재사용 불가).
 * **검증:** 제시된 토큰을 같은 방식으로 해시해 저장소에서 조회한다(키 조회, 비교 연산 없음).
 * **회전:** refresh는 1회용 — 사용 즉시 폐기하고 새 토큰쌍을 발급한다(탈취 토큰 재사용 차단).
 * **폐기:** 로그아웃·계정삭제 시 해당 토큰을 저장소에서 삭제한다(access 짧은 TTL과 합쳐 즉시성 확보).
 */
@Service
class TokenService(
    private val store: AuthTokenStore,
    private val properties: AuthTokenProperties,
) {

    private val random = SecureRandom()

    /** 로그인·가입 성공 시 새 access/refresh 토큰쌍을 발급해 저장한다. */
    fun issue(userId: UserId): IssuedTokens {
        val accessToken = newToken()
        val refreshToken = newToken()
        store.save(TokenKind.ACCESS, hash(accessToken), userId.value, properties.accessTtl)
        store.save(TokenKind.REFRESH, hash(refreshToken), userId.value, properties.refreshTtl)
        return IssuedTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTtl = properties.accessTtl,
            refreshTtl = properties.refreshTtl,
        )
    }

    /** access 토큰으로 현재 사용자를 해석한다(없거나 만료면 null). */
    fun authenticate(accessToken: String): UserId? =
        store.findUserId(TokenKind.ACCESS, hash(accessToken))?.let { UserId(it) }

    /**
     * refresh 토큰 회전: 유효하면 기존 refresh를 즉시 폐기하고 새 토큰쌍을 발급해 돌려준다. 무효면 null.
     * (1회용 회전 — 같은 refresh 재사용 불가. 이전 access는 짧은 TTL로 곧 만료된다.)
     */
    fun refresh(refreshToken: String): IssuedTokens? {
        val refreshHash = hash(refreshToken)
        val userId = store.findUserId(TokenKind.REFRESH, refreshHash) ?: return null
        store.delete(TokenKind.REFRESH, refreshHash)
        return issue(UserId(userId))
    }

    /** 로그아웃·계정삭제 시 제시된 토큰들을 폐기한다(null은 무시). */
    fun revoke(accessToken: String?, refreshToken: String?) {
        accessToken?.let { store.delete(TokenKind.ACCESS, hash(it)) }
        refreshToken?.let { store.delete(TokenKind.REFRESH, hash(it)) }
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** 토큰 엔트로피(바이트). 32바이트(256비트)면 추측 불가능에 충분하다. */
        private const val TOKEN_BYTES = 32
    }
}
