package watson.resumaker.auth.application

import java.time.Duration

/**
 * 새로 발급된 토큰쌍(원문)과 각 수명. 쿠키 계층(presentation)이 이 값으로 HttpOnly 쿠키를 굽는다.
 *
 * 원문 토큰은 응답으로 단 한 번 클라이언트에 내려가고, 서버는 해시만 저장한다([AuthTokenStore]).
 */
data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTtl: Duration,
    val refreshTtl: Duration,
)
