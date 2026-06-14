package watson.resumaker.account.application

import org.springframework.stereotype.Component
import watson.resumaker.account.domain.User
import watson.resumaker.account.presentation.SignUpResponse

/**
 * 서비스 계층의 Response DTO 변환을 담당한다(아키텍처 가이드: Service Mapper).
 */
@Component
class AccountServiceMapper {

    fun toSignUpResponse(user: User): SignUpResponse =
        SignUpResponse(userId = user.id.value.toString())
}
