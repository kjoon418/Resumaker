package watson.resumaker.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** WX-8: 화면 ↔ URL 경로 매핑 왕복·딥링크 파싱 검증. */
class RoutesTest {

    @Test
    fun pathOfCoversAllRoots() {
        assertEquals("/", Routes.pathOf(Screen.Home))
        assertEquals("/session", Routes.pathOf(Screen.Session))
        assertEquals("/experiences", Routes.pathOf(Screen.ExperienceList))
        assertEquals("/targets", Routes.pathOf(Screen.TargetList))
        assertEquals("/artifact", Routes.pathOf(Screen.Artifact()))
        assertEquals("/me", Routes.pathOf(Screen.MyPage))
    }

    @Test
    fun pathOfEncodesEditIds() {
        assertEquals("/experiences/new", Routes.pathOf(Screen.ExperienceEdit(null)))
        assertEquals("/experiences/abc", Routes.pathOf(Screen.ExperienceEdit("abc")))
        assertEquals("/targets/new", Routes.pathOf(Screen.TargetEdit(null)))
        assertEquals("/targets/xyz", Routes.pathOf(Screen.TargetEdit("xyz")))
    }

    @Test
    fun screenOfRoundTripsRoots() {
        listOf(
            Screen.Home, Screen.Session, Screen.ExperienceList, Screen.TargetList, Screen.MyPage,
        ).forEach { screen ->
            assertEquals(screen, Routes.screenOf(Routes.pathOf(screen)))
        }
    }

    @Test
    fun screenOfParsesEditDeepLinks() {
        assertEquals(Screen.ExperienceEdit(null), Routes.screenOf("/experiences/new"))
        assertEquals(Screen.ExperienceEdit("abc"), Routes.screenOf("/experiences/abc"))
        assertEquals(Screen.TargetEdit(null), Routes.screenOf("/targets/new"))
        assertEquals(Screen.TargetEdit("xyz"), Routes.screenOf("/targets/xyz"))
    }

    @Test
    fun screenOfEmptyPathIsHome() {
        assertEquals(Screen.Home, Routes.screenOf("/"))
        assertEquals(Screen.Home, Routes.screenOf(""))
    }

    @Test
    fun screenOfIgnoresQueryAndHash() {
        assertEquals(Screen.ExperienceList, Routes.screenOf("/experiences?foo=1#bar"))
    }

    @Test
    fun screenOfUnknownPathIsNull() {
        assertNull(Routes.screenOf("/nope/here"))
    }
}
