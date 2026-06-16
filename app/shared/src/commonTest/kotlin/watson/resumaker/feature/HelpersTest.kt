package watson.resumaker.feature

import watson.resumaker.feature.experience.formatPeriod
import watson.resumaker.feature.target.targetTitle
import watson.resumaker.feature.template.presetSectionSummary
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplatePresetResponse
import watson.resumaker.model.type.SectionCharacter
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

    private fun preset(vararg names: String) = TemplatePresetResponse(
        key = "k",
        name = "n",
        sections = names.map { SectionResponse(name = it, character = SectionCharacter.SUMMARY) },
    )

    @Test
    fun presetSummaryThreeSectionsHasNoMoreSuffix() {
        assertEquals("섹션 3개 · A · B · C", presetSectionSummary(preset("A", "B", "C")))
    }

    @Test
    fun presetSummaryFourSectionsAddsRemainderSuffix() {
        assertEquals("섹션 4개 · A · B · C +1개 더", presetSectionSummary(preset("A", "B", "C", "D")))
    }

    @Test
    fun presetSummaryNoSectionsShowsCountOnly() {
        assertEquals("섹션 0개", presetSectionSummary(preset()))
    }
}
