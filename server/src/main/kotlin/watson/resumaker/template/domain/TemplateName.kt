package watson.resumaker.template.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 이력서 양식 이름(필수). 공백 불가, 최대 길이(도메인 이해 §2.5).
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class TemplateName(val value: String) {

    init {
        if (value.isBlank()) {
            throw DomainValidationException("양식 이름을 적어 주세요. 예: 토스 백엔드 지원용")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("양식 이름은 ${MAX_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_LENGTH = 100
    }
}
