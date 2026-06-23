package watson.resumaker.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** 헤더 탭 라벨·순서·개수 회귀 방어(WX-7). 탭 추가/순서 변경 시 의도된 변경만 통과시킨다. */
class AppHeaderTest {

    @Test
    fun headerTabsAreSixInOrder() {
        assertEquals(
            listOf("홈", "경험", "목표", "양식", "만들기", "산출물"),
            HeaderTab.entries.map { it.label },
        )
    }
}
