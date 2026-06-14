package watson.resumaker.experience.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 경험 기록 제목. 공백 불가, 최대 길이 제한(구현 설계 §3.3).
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class ExperienceTitle(val value: String) {

    init {
        if (value.isBlank()) {
            throw DomainValidationException("경험의 제목을 입력해 주세요.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("제목은 ${MAX_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_LENGTH = 100
    }
}
