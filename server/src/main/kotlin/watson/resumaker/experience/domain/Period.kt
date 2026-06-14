package watson.resumaker.experience.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import watson.resumaker.common.domain.DomainValidationException
import java.time.LocalDate

/**
 * 경험 기간(시작·종료). 다중 필드 VO이므로 @Embeddable.
 * 불변식: 시작 <= 종료(구현 설계 §3.3).
 */
@Embeddable
class Period private constructor(
    @Column(name = "period_start")
    val start: LocalDate,
    @Column(name = "period_end")
    val end: LocalDate,
) {

    init {
        if (start.isAfter(end)) {
            throw DomainValidationException("기간의 시작일이 종료일보다 늦을 수 없어요.")
        }
    }

    companion object {
        fun of(start: LocalDate, end: LocalDate): Period = Period(start, end)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Period) return false
        return start == other.start && end == other.end
    }

    override fun hashCode(): Int = 31 * start.hashCode() + end.hashCode()
}
