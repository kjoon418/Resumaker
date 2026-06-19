package watson.resumaker.auth.application

import java.time.Duration
import java.util.UUID

/**
 * 인증 토큰 저장소 포트(불투명 토큰 모델). 토큰 원문이 아니라 **해시 → 사용자 매핑**을 TTL과 함께 보관한다.
 *
 * 구현은 교체 가능하다(현재 Redis — 네이티브 TTL로 만료·자동 로그아웃, DEL로 즉시 폐기). 향후 부하/토폴로지에 따라
 * 다른 저장소로 갈아끼워도 인증 로직([TokenService])은 바뀌지 않는다. 저장소가 곧 진실의 원천이므로,
 * 핫패스 비용·폐기 즉시성은 이 포트의 구현 선택으로 조정한다.
 *
 * 토큰 원문은 절대 저장하지 않는다(저장소 유출 시에도 재사용 불가). 조회는 제시된 토큰을 같은 방식으로 해시해
 * 키로 사용한다.
 */
interface AuthTokenStore {

    /** [tokenHash]→[userId] 매핑을 [ttl] 동안 저장한다(만료되면 저장소가 자동 제거). */
    fun save(kind: TokenKind, tokenHash: String, userId: UUID, ttl: Duration)

    /** [tokenHash]에 매핑된 사용자(없거나 만료면 null). */
    fun findUserId(kind: TokenKind, tokenHash: String): UUID?

    /** [tokenHash] 매핑을 즉시 제거한다(로그아웃·회전·폐기). 없으면 무시한다. */
    fun delete(kind: TokenKind, tokenHash: String)
}

/** 토큰 종류. 저장소 키 네임스페이스를 분리해 access/refresh가 섞이지 않게 한다. */
enum class TokenKind {
    ACCESS,
    REFRESH,
}
