package watson.resumaker.experience.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 사용 역량·기술 태그. 공백 불가, 길이 제한(구현 설계 §3.3).
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class SkillTag(val value: String) {

    init {
        if (value.isBlank()) {
            throw DomainValidationException("역량 태그는 비어 있을 수 없어요.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("역량 태그는 ${MAX_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_LENGTH = 50
    }
}
