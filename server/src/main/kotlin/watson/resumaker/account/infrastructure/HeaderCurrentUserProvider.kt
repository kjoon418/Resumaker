package watson.resumaker.account.infrastructure

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.UnauthorizedException
import java.util.UUID

/**
 * 요청 헤더 X-User-Id에서 현재 사용자를 해석한다(구현 설계 §12 — 후속에서 JWT 등으로 대체 예정).
 */
@Component
class HeaderCurrentUserProvider(
    private val request: HttpServletRequest,
) : CurrentUserProvider {

    override fun currentUserId(): UserId {
        val header = request.getHeader(USER_ID_HEADER)
            ?: throw UnauthorizedException("로그인 정보가 필요해요. 다시 로그인해 주세요.")

        return try {
            UserId(UUID.fromString(header))
        } catch (exception: IllegalArgumentException) {
            throw UnauthorizedException("로그인 정보가 올바르지 않아요. 다시 로그인해 주세요.")
        }
    }

    companion object {
        const val USER_ID_HEADER = "X-User-Id"
    }
}
