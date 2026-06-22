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
        assertEquals("/targets/xyz/edit", Routes.pathOf(Screen.TargetEdit("xyz")))
        assertEquals("/targets/xyz", Routes.pathOf(Screen.TargetDetail("xyz")))
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
        // /targets/{id} 는 상세, /targets/{id}/edit 는 수정으로 매핑된다(상세 신설·라우팅 재배치).
        assertEquals(Screen.TargetDetail("xyz"), Routes.screenOf("/targets/xyz"))
        assertEquals(Screen.TargetEdit("xyz"), Routes.screenOf("/targets/xyz/edit"))
    }

    @Test
    fun targetDetailAndEditRoundTrip() {
        // 상세: /targets/{id} 왕복. 수정(3세그먼트)이 상세(2세그먼트)에 가려지지 않는다.
        assertEquals("/targets/t-1", Routes.pathOf(Screen.TargetDetail("t-1")))
        assertEquals(Screen.TargetDetail("t-1"), Routes.screenOf("/targets/t-1"))
        assertEquals("/targets/t-1/edit", Routes.pathOf(Screen.TargetEdit("t-1")))
        assertEquals(Screen.TargetEdit("t-1"), Routes.screenOf("/targets/t-1/edit"))
        // 신규 생성은 여전히 /targets/new.
        assertEquals(Screen.TargetEdit(null), Routes.screenOf("/targets/new"))
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
    fun artifactListRoundTrips() {
        // 내 산출물 목록: /artifacts(1세그먼트). 2세그먼트 열람·3세그먼트 버전과 구분된다.
        assertEquals("/artifacts", Routes.pathOf(Screen.ArtifactList))
        assertEquals(Screen.ArtifactList, Routes.screenOf("/artifacts"))
    }

    @Test
    fun artifactViewEncodesIdAndRestoresWithoutInitial() {
        // 열람: id 는 URL 에 담기고, transient initial 은 비참여라 딥링크 복원 시 null 이다.
        assertEquals("/artifacts/a-1", Routes.pathOf(Screen.ArtifactView("a-1")))
        assertEquals(Screen.ArtifactView("a-1"), Routes.screenOf("/artifacts/a-1"))
    }

    @Test
    fun artifactVersionsRoundTrips() {
        // 버전 기록·비교: /artifacts/{id}/versions 왕복. 3세그먼트가 2세그먼트 열람보다 먼저 매칭돼야 한다.
        assertEquals("/artifacts/a-1/versions", Routes.pathOf(Screen.ArtifactVersions("a-1")))
        assertEquals(Screen.ArtifactVersions("a-1"), Routes.screenOf("/artifacts/a-1/versions"))
        // 열람 경로(2세그먼트)는 여전히 열람으로 매칭(버전 경로에 가려지지 않음).
        assertEquals(Screen.ArtifactView("a-1"), Routes.screenOf("/artifacts/a-1"))
    }
}
