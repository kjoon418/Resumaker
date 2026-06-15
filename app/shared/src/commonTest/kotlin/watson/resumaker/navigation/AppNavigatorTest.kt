package watson.resumaker.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** WX-4/8: 저장 후 목록 복귀 규칙·성공 메시지·popstate 동기화 검증(history=null 인메모리). */
class AppNavigatorTest {

    @Test
    fun returnToListFromHomeEntryGoesToListWithMessage() {
        // 홈에서 신규 경험 진입 후 저장 → 경험 목록으로 수렴(WX-4).
        val nav = AppNavigator(Screen.Home)
        nav.push(Screen.ExperienceEdit(null))

        nav.returnToList(Screen.ExperienceList, "경험을 저장했어요")

        assertEquals(Screen.ExperienceList, nav.current)
        assertEquals("경험을 저장했어요", nav.consumePendingMessage())
    }

    @Test
    fun returnToListFromListEntryUnwindsToExistingList() {
        // 목록 → 편집 진입 후 저장 → 같은 목록 인스턴스로 되감기.
        val nav = AppNavigator(Screen.ExperienceList)
        nav.push(Screen.ExperienceEdit("a"))

        nav.returnToList(Screen.ExperienceList, "경험을 저장했어요")

        assertEquals(Screen.ExperienceList, nav.current)
    }

    @Test
    fun pendingMessageIsConsumedOnce() {
        val nav = AppNavigator(Screen.TargetList)
        nav.push(Screen.TargetEdit(null))
        nav.returnToList(Screen.TargetList, "목표를 저장했어요")

        assertEquals("목표를 저장했어요", nav.consumePendingMessage())
        // 두 번째 읽기는 null(중복 노출 방지).
        assertNull(nav.consumePendingMessage())
    }

    @Test
    fun switchRootDoesNotForceBackToHome() {
        // WX-4: 목록 진입은 switchRoot지만, 편집은 push라 back(pop)이 "홈"이 아닌 목록으로.
        val nav = AppNavigator(Screen.Home)
        nav.switchRoot(Screen.ExperienceList)
        nav.push(Screen.ExperienceEdit("a"))

        nav.pop()

        assertEquals(Screen.ExperienceList, nav.current)
    }

    @Test
    fun syncFromPathReflectsBrowserNavigation() {
        // WX-8: popstate 경로를 현재 화면에 반영.
        val nav = AppNavigator(Screen.Home)
        nav.push(Screen.ExperienceList)

        nav.syncFromPath("/targets")
        assertEquals(Screen.TargetList, nav.current)
    }

    @Test
    fun syncFromPathIgnoresUnknown() {
        val nav = AppNavigator(Screen.Home)
        nav.syncFromPath("/garbage")
        assertEquals(Screen.Home, nav.current)
    }
}
