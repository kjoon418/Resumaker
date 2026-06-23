package watson.resumaker.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** 탭 목적지 헤더 폭 정책 회귀 방어(WX-7). 탭마다 헤더 폭이 달라 공유 크롬이 튀는 것을 막는다. */
class TabDestinationWidthTest {

    @Test
    fun allTabDestinationsShareSameHeaderWidth() {
        val widths = HeaderTab.entries.map { headerWidthForTab(it) }.distinct()
        assertEquals(1, widths.size, "탭 목적지 헤더 폭은 모두 동일해야 한다(공유 크롬 폭 불일치 금지)")
        assertEquals(ContentWidth.WIDE, widths.single())
    }
}
