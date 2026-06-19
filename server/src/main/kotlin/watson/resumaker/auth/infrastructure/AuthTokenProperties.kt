package watson.resumaker.auth.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 인증 토큰 수명 설정(외부화). access는 짧게(요청 인증 창), refresh는 길게(재발급) 둔다.
 *
 * - [accessTtl]: access 토큰 유효 기간. 짧을수록 탈취·폐기 지연 창이 작다(기본 30분).
 * - [refreshTtl]: refresh 토큰 유효 기간(기본 14일). 사용 시 회전되며, 만료·로그아웃·계정삭제로 폐기된다.
 *
 * ISO-8601 Duration 문자열로 주입한다(예: PT30M, P14D).
 */
@ConfigurationProperties(prefix = "resumaker.auth")
data class AuthTokenProperties(
    val accessTtl: Duration = Duration.ofMinutes(30),
    val refreshTtl: Duration = Duration.ofDays(14),
    // 쿠키 보안 속성. 다른 도메인(cross-site) 배포 전제로 기본 Secure + SameSite=None.
    // 로컬 http(localhost)도 브라우저가 Secure 쿠키 저장을 허용한다. 운영은 HTTPS 필수.
    val cookieSecure: Boolean = true,
    val cookieSameSite: String = "None",
)
