package watson.resumaker.account.application

import watson.resumaker.account.domain.UserId

/**
 * 현재 요청의 인증 주체를 해석한다.
 *
 * MVP에서는 요청 헤더 X-User-Id로 사용자를 식별한다. 실제 로그인/JWT는 구현 설계 §12대로 후속으로 미룬다.
 * 헤더가 없거나 형식이 잘못되면 UnauthorizedException을 던진다.
 */
interface CurrentUserProvider {

    fun currentUserId(): UserId
}
