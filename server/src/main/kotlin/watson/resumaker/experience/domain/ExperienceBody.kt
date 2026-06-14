package watson.resumaker.experience.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 경험 본문(무엇을 했는가). 필수값이며 공백 불가(기능 1 실패 케이스: 본문이 비면 생성 재료가 없으므로 거부).
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class ExperienceBody(val value: String) {

    init {
        if (value.isBlank()) {
            throw DomainValidationException("이 경험에서 무슨 일을 했는지 한 줄이라도 적어 주세요. 나중에 더 자세히 보강할 수 있어요.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("본문은 ${MAX_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_LENGTH = 5000
    }
}
