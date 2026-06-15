package watson.resumaker.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** WX-1/5: 브레이크포인트 구간·그리드 열수 계산 검증. */
class ResponsiveTest {

    @Test
    fun windowSizeBreakpoints() {
        assertEquals(WindowSize.COMPACT, windowSizeFor(599.dp))
        assertEquals(WindowSize.MEDIUM, windowSizeFor(600.dp))
        assertEquals(WindowSize.MEDIUM, windowSizeFor(1023.dp))
        assertEquals(WindowSize.EXPANDED, windowSizeFor(1024.dp))
        assertEquals(WindowSize.EXPANDED, windowSizeFor(1920.dp))
    }

    @Test
    fun pagePaddingPerWindow() {
        assertEquals(RmSize.pagePaddingCompact, WindowSize.COMPACT.pagePadding())
        assertEquals(RmSize.pagePaddingMedium, WindowSize.MEDIUM.pagePadding())
        assertEquals(RmSize.pagePaddingExpanded, WindowSize.EXPANDED.pagePadding())
    }

    @Test
    fun gridColumnsClampedByWindow() {
        // Compact는 항상 1열.
        assertEquals(1, gridColumnsFor(WindowSize.COMPACT, 1120.dp))
        // Medium은 최대 2열.
        assertEquals(2, gridColumnsFor(WindowSize.MEDIUM, 1120.dp))
        // Expanded 1120dp / 320dp 최소셀 = 3열.
        assertEquals(3, gridColumnsFor(WindowSize.EXPANDED, 1120.dp))
    }

    @Test
    fun gridColumnsAtLeastOne() {
        // 매우 좁은 폭이라도 최소 1열.
        assertEquals(1, gridColumnsFor(WindowSize.EXPANDED, 100.dp))
    }

    @Test
    fun gridColumnsExpandedNarrowContent() {
        // Expanded지만 콘텐츠 폭이 640dp면 320 최소셀 기준 2열.
        assertEquals(2, gridColumnsFor(WindowSize.EXPANDED, 640.dp))
    }
}
