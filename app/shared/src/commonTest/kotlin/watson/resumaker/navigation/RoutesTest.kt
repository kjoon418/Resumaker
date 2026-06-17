package watson.resumaker.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** WX-8/CQ-4/CQ-5: 화면 ↔ URL 경로 매핑 왕복·딥링크 파싱·엣지 케이스 검증. */
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

    // ── CQ-5 추가 엣지 케이스 ───────────────────────────────────────────────

    @Test
    fun screenOfTrailingSlashIsHandled() {
        // CQ-5: 트레일링 슬래시는 세그먼트 필터로 제거돼 정상 화면 반환.
        assertEquals(Screen.ExperienceList, Routes.screenOf("/experiences/"))
        assertEquals(Screen.TargetList, Routes.screenOf("/targets/"))
        assertEquals(Screen.Home, Routes.screenOf("/"))
    }

    @Test
    fun screenOfDoubleSlashSegmentsFiltered() {
        // CQ-5: 연속 슬래시(빈 세그먼트)는 필터로 제거돼 정상 파싱.
        assertEquals(Screen.ExperienceList, Routes.screenOf("//experiences"))
        assertEquals(Screen.Home, Routes.screenOf("//"))
    }

    @Test
    fun artifactRoundTripIsTransient() {
        // CQ-4: Artifact 는 transient — pathOf 는 "/artifact", screenOf 는 기본값(hasExperiences=true).
        // hasExperiences 는 URL 에 직렬화되지 않으므로 round-trip 에서 기본값만 복원된다.
        val artifactWithFalse = Screen.Artifact(hasExperiences = false)
        val path = Routes.pathOf(artifactWithFalse)
        assertEquals("/artifact", path)

        // screenOf 는 항상 기본값 true 반환(boolean 은 URL 비참여).
        val restored = Routes.screenOf(path)
        assertEquals(Screen.Artifact(hasExperiences = true), restored)
        // hasExperiences 복원 불가 — 진입 시 소스(ViewModel)가 값을 공급해야 한다.
    }

    @Test
    fun artifactViewEncodesIdAndRestoresWithoutInitial() {
        // 열람: id 는 URL 에 담기고, transient initial 은 비참여라 딥링크 복원 시 null 이다.
        assertEquals("/artifacts/a-1", Routes.pathOf(Screen.ArtifactView("a-1")))
        assertEquals(Screen.ArtifactView("a-1"), Routes.screenOf("/artifacts/a-1"))
    }
}
