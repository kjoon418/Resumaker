package watson.resumaker.auth.application

import java.time.Duration
import java.util.UUID

/**
 * 테스트용 인메모리 [AuthTokenStore] 페이크. 실제 Redis 없이 토큰 로직([TokenService])을 결정적으로 검증한다.
 * TTL은 로직 검증에 불필요하므로 보관만 하고 만료는 시뮬레이션하지 않는다(만료는 저장소(Redis) 책임).
 */
class InMemoryAuthTokenStore : AuthTokenStore {

    private val entries = mutableMapOf<String, UUID>()

    override fun save(kind: TokenKind, tokenHash: String, userId: UUID, ttl: Duration) {
        entries[key(kind, tokenHash)] = userId
    }

    override fun findUserId(kind: TokenKind, tokenHash: String): UUID? = entries[key(kind, tokenHash)]

    override fun delete(kind: TokenKind, tokenHash: String) {
        entries.remove(key(kind, tokenHash))
    }

    private fun key(kind: TokenKind, tokenHash: String): String = "${kind.name}:$tokenHash"
}
