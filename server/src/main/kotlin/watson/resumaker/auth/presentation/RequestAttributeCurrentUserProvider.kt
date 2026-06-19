package watson.resumaker.auth.presentation

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.UnauthorizedException

/**
 * [TokenAuthenticationFilter]가 access 쿠키를 검증해 채워 둔 요청 속성에서 현재 사용자를 읽는다.
 * 속성이 없으면(유효한 access 토큰 없음) 인증 실패로 401을 던진다(기존 X-User-Id 헤더 기반 provider 대체).
 */
@Component
class RequestAttributeCurrentUserProvider(
    private val request: HttpServletRequest,
) : CurrentUserProvider {

    override fun currentUserId(): UserId =
        request.getAttribute(TokenAuthenticationFilter.AUTH_USER_ATTRIBUTE) as? UserId
            ?: throw UnauthorizedException("로그인 정보가 필요해요. 다시 로그인해 주세요.")
}
