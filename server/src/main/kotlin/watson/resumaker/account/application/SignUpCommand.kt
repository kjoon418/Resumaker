package watson.resumaker.account.application

import watson.resumaker.account.domain.Credential
import watson.resumaker.account.domain.UserTimeZone

/**
 * 회원가입 서비스 입력 DTO. 원시 값 대신 VO를 받는다(검증 가이드).
 * 비밀번호 해싱은 presentation의 Mapper가 수행해 Credential로 포장한다.
 */
data class SignUpCommand(
    val credential: Credential,
    val timeZone: UserTimeZone,
)
