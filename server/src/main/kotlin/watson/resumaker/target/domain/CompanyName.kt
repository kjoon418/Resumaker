package watson.resumaker.target.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 회사명(선택). 값이 있을 경우 공백 불가·길이 제한(구현 설계 §3.4).
 * 선택값이므로 미입력(null)은 엔티티 수준에서 허용한다.
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class CompanyName(val value: String) {

    init {
        if (value.isBlank()) {
            throw DomainValidationException("회사명은 비어 있을 수 없어요.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("회사명은 ${MAX_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_LENGTH = 100
    }
}
