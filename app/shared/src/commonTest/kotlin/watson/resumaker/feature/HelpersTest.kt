package watson.resumaker.feature

import watson.resumaker.feature.experience.formatPeriod
import watson.resumaker.feature.target.targetTitle
import watson.resumaker.model.dto.TargetResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HelpersTest {

    @Test
    fun formatPeriodBothPresent() {
        assertEquals("2024-01-01 ~ 2024-03-01", formatPeriod("2024-01-01", "2024-03-01"))
    }

    @Test
    fun formatPeriodStartOnly() {
        assertEquals("2024-01-01 ~", formatPeriod("2024-01-01", null))
    }

    @Test
    fun formatPeriodNonePresentIsNull() {
        assertNull(formatPeriod(null, null))
    }

    @Test
    fun targetTitleCombinesCompanyAndJob() {
        val t = TargetResponse(id = "1", recruitDirection = "d", companyName = "토스", jobTitle = "백엔드")
        assertEquals("토스 · 백엔드", targetTitle(t))
    }

    @Test
    fun targetTitleFallsBackWhenEmpty() {
        val t = TargetResponse(id = "1", recruitDirection = "d", companyName = null, jobTitle = null)
        assertEquals("겨냥하는 목표", targetTitle(t))
    }
}
