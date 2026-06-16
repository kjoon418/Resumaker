package watson.resumaker.artifact.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import watson.resumaker.common.domain.DomainValidationException

/**
 * 생성 항목의 내용(생성된 텍스트 본문). 의미 있는 단일 값이지만 @Embeddable VO로 두어
 * ArtifactSection 엔티티에 인라인 컬럼으로 매핑한다.
 *
 * 부분 실패 항목(*_FAILED)은 내용이 비어 있을 수 있으므로(아직 생성 전/실패) 공백을 허용한다.
 * 즉 SectionContent는 내용 유무를 강제하지 않는다 — 상태(SectionStatus)가 의미를 규정한다.
 */
@Embeddable
class SectionContent private constructor(
    @Column(name = "content", nullable = false, length = MAX_LENGTH)
    val value: String,
) {

    companion object {
        const val MAX_LENGTH = 10000

        val EMPTY: SectionContent = SectionContent("")

        fun of(value: String): SectionContent {
            if (value.length > MAX_LENGTH) {
                throw DomainValidationException("생성 항목 내용은 ${MAX_LENGTH}자 이내여야 해요.")
            }
            return SectionContent(value)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SectionContent) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
