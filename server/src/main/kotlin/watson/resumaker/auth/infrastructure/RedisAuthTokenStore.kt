package watson.resumaker.auth.infrastructure

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import watson.resumaker.auth.application.AuthTokenStore
import watson.resumaker.auth.application.TokenKind
import java.time.Duration
import java.util.UUID

/**
 * [AuthTokenStore]의 Redis 구현. 키 `auth:{kind}:{tokenHash}` → 값 `userId`로 저장하고 네이티브 TTL을 건다.
 *
 * - **만료·자동 로그아웃:** Redis TTL이 만료된 키를 자동 제거한다(별도 정리 작업 불필요).
 * - **즉시 폐기:** [delete]가 키를 즉시 지운다(로그아웃·회전·계정삭제). 다중 인스턴스에서도 공유 저장소라 전역 반영.
 * - **저비용 검증:** O(1) 키 조회로 RDBMS 인덱스 쿼리보다 가볍고, 주 DB에서 인증 핫스팟을 분리한다.
 */
@Repository
class RedisAuthTokenStore(
    private val redis: StringRedisTemplate,
) : AuthTokenStore {

    override fun save(kind: TokenKind, tokenHash: String, userId: UUID, ttl: Duration) {
        redis.opsForValue().set(key(kind, tokenHash), userId.toString(), ttl)
    }

    override fun findUserId(kind: TokenKind, tokenHash: String): UUID? =
        redis.opsForValue().get(key(kind, tokenHash))?.let(UUID::fromString)

    override fun delete(kind: TokenKind, tokenHash: String) {
        redis.delete(key(kind, tokenHash))
    }

    private fun key(kind: TokenKind, tokenHash: String): String =
        "auth:${kind.name.lowercase()}:$tokenHash"
}
