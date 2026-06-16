package watson.resumaker.artifact.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 산출물에 등장한 수치/고유명사 토큰(도메인 이해 §381 층위 2).
 *
 * value class로 두되 도메인 불변식(공백 금지)을 동반 검증한다. FactGrounding은 @Embeddable이며
 * value class 필드를 @Embeddable에 직접 두면 JdbcType 추론 함정에 걸리므로(SkillTagConverter 주석),
 * FactGrounding은 이 토큰을 원시 String 컬럼으로 저장하고 경계에서 VO로 감싼다.
 */
@JvmInline
value class FactToken private constructor(val value: String) {

    companion object {
        const val MAX_LENGTH = 500

        fun of(value: String): FactToken {
            if (value.isBlank()) {
                throw DomainValidationException("사실 근거 토큰은 비어 있을 수 없어요.")
            }
            if (value.length > MAX_LENGTH) {
                throw DomainValidationException("사실 근거 토큰은 ${MAX_LENGTH}자 이내여야 해요.")
            }
            return FactToken(value)
        }

        /**
         * DB에서 복원한 신뢰 가능한 값을 재검증 없이 래핑한다(게터 throw 제거).
         * 불변식은 write 경계(of)에서만 강제하며, 저장된 값은 이미 그 경계를 통과했으므로 안전하다.
         * 도메인 외부에서 호출하지 못하도록 internal로 제한한다.
         */
        internal fun ofTrusted(value: String): FactToken = FactToken(value)
    }
}
