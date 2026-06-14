package watson.resumaker.target.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 채용 방향 텍스트(필수). 공백 불가, 최대 길이(구현 설계 §3.4, 기능 2 실패 케이스: 빈 방향 거부).
 * 단일값 VO는 @JvmInline value class로 둔다. JPA는 Kotlin inline class를 내부 타입(String)으로 매핑·복원한다.
 */
@JvmInline
value class RecruitDirection(val value: String) {

    init {
        if (value.isBlank()) {
            throw DomainValidationException("어떤 회사·직무를 겨냥하는지 알려주시면 그 방향에 맞춰 만들어 드려요. 공고 내용을 붙여넣어도 좋아요.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("채용 방향은 ${MAX_LENGTH}자 이내로 적어 주세요.")
        }
    }

    companion object {
        const val MAX_LENGTH = 5000
    }
}
