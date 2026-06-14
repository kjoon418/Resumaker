package watson.resumaker.account.domain

import watson.resumaker.common.domain.DomainValidationException
import java.time.DateTimeException
import java.time.ZoneId

/**
 * 사용자 시간대. 비용 가드레일 리셋 경계(달력일) 계산에 쓰인다(구현 설계 §3.2, §7).
 * 유효한 시간대 문자열인지 검증한다.
 *
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class UserTimeZone(val value: String) {

    init {
        try {
            ZoneId.of(value)
        } catch (exception: DateTimeException) {
            throw DomainValidationException("시간대 정보가 올바르지 않아요. 예: Asia/Seoul")
        }
    }

    fun toZoneId(): ZoneId = ZoneId.of(value)

    companion object {
        val DEFAULT: UserTimeZone = UserTimeZone("Asia/Seoul")
    }
}
