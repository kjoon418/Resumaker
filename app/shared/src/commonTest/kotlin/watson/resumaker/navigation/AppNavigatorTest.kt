package watson.resumaker.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WX-4/8/CQ-1/CQ-5: 저장 후 목록 복귀 규칙·성공 메시지·popstate 동기화·history 상호작용 검증. */
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
    fun switchRootToArtifactResetsStackToSingleRoot() {
        // 헤더 '만들기' 탭(switchRoot(Screen.Artifact))은 push 단계가 쌓여 있어도
        // 스택을 단일 루트로 리셋한다 — 진입 후 뒤로가기 불가.
        val nav = AppNavigator(Screen.Home)
        nav.push(Screen.ExperienceList)
        nav.push(Screen.ExperienceEdit("a"))

        nav.switchRoot(Screen.Artifact())

        assertEquals(Screen.Artifact(), nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun switchRootToArtifactListResetsStackToSingleRoot() {
        // 헤더 '산출물' 탭(switchRoot(Screen.ArtifactList))도 동일하게 단일 루트로 리셋한다.
        val nav = AppNavigator(Screen.Home)
        nav.push(Screen.ExperienceList)
        nav.push(Screen.ExperienceEdit("a"))

        nav.switchRoot(Screen.ArtifactList)

        assertEquals(Screen.ArtifactList, nav.current)
        assertFalse(nav.canGoBack)
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

    // ── CQ-5 추가 케이스 ────────────────────────────────────────────────────

    @Test
    fun popOnRootIsNoOp() {
        // CQ-5: 루트(빈 backStack == 1)에서 pop()은 current 를 바꾸지 않는다.
        val nav = AppNavigator(Screen.Home)
        assertFalse(nav.canGoBack)

        nav.pop()

        assertEquals(Screen.Home, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun returnToListMakesListRootWhenNotInStack() {
        // CQ-5: backStack 에 목록이 없으면 목록을 단일 루트로 세운다(else 분기).
        val nav = AppNavigator(Screen.Home)
        // Home → ExperienceEdit(new) — ExperienceList 는 스택에 없음.
        nav.push(Screen.ExperienceEdit(null))

        nav.returnToList(Screen.ExperienceList, "경험을 저장했어요")

        assertEquals(Screen.ExperienceList, nav.current)
        // 목록이 루트여야 하므로 더 이상 back 불가.
        assertFalse(nav.canGoBack)
    }

    // ── L-3: replaceTop(원자적 top 교체) ───────────────────────────────────

    @Test
    fun replaceTopSwapsCurrentWithoutGrowingStack() {
        // L-3: 프리셋 선택 흐름(목록 → 프리셋 → 편집)에서 프리셋을 편집으로 원자 교체.
        // pop→push 이중 호출과 달리 스택 깊이는 유지되고 top만 바뀐다.
        val nav = AppNavigator(Screen.TemplateList)
        nav.push(Screen.TemplatePreset)

        nav.replaceTop(Screen.TemplateEdit(null))

        assertEquals(Screen.TemplateEdit(null), nav.current)
        // 목록 위 한 단계만 남으므로 back은 목록으로(중간 URL이 history에 쌓이지 않음).
        assertTrue(nav.canGoBack)
        nav.pop()
        assertEquals(Screen.TemplateList, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun replaceTopOnRootKeepsSingleRoot() {
        // 루트에서 replaceTop은 루트 자체를 교체하되 스택 깊이는 1로 유지(back 불가).
        val nav = AppNavigator(Screen.TemplateList)

        nav.replaceTop(Screen.TemplateInterpret)

        assertEquals(Screen.TemplateInterpret, nav.current)
        assertFalse(nav.canGoBack)
    }

    // ── #6 뒤로가기 fallback(히스토리 없음 직접 진입) ──────────────────────────

    @Test
    fun popOnRootNavigatesToLogicalParent() {
        // #6: 직접 진입(history=null, backStack 크기 1)에서 pop()은 논리적 상위로 이동한다.
        // 기존 목표 수정의 상위는 상세(라우팅 재배치).
        val nav = AppNavigator(Screen.TargetEdit(targetId = "t-1"))
        assertFalse(nav.canGoBack)

        nav.pop()

        assertEquals(Screen.TargetDetail("t-1"), nav.current)
        // 상위(상세)가 루트가 되므로 더 이상 back 불가.
        assertFalse(nav.canGoBack)
    }

    @Test
    fun popOnRootNewTargetEditGoesToTargetList() {
        // 신규 목표 생성(targetId=null)의 상위는 목록이다.
        val nav = AppNavigator(Screen.TargetEdit(targetId = null))
        nav.pop()
        assertEquals(Screen.TargetList, nav.current)
    }

    @Test
    fun popOnRootTargetDetailGoesToTargetList() {
        val nav = AppNavigator(Screen.TargetDetail("t-1"))
        nav.pop()
        assertEquals(Screen.TargetList, nav.current)
    }

    @Test
    fun popOnRootExperienceEditGoesToExperienceList() {
        val nav = AppNavigator(Screen.ExperienceEdit(experienceId = "e-1"))
        nav.pop()
        assertEquals(Screen.ExperienceList, nav.current)
    }

    @Test
    fun popOnRootArtifactViewGoesToArtifactList() {
        // 산출물은 목록을 거쳐 열람하므로, 열람 화면의 논리적 상위는 산출물 목록이다(#6).
        val nav = AppNavigator(Screen.ArtifactView(artifactId = "a-1"))
        nav.pop()
        assertEquals(Screen.ArtifactList, nav.current)
    }

    @Test
    fun popOnRootArtifactListGoesToHome() {
        val nav = AppNavigator(Screen.ArtifactList)
        nav.pop()
        assertEquals(Screen.Home, nav.current)
    }

    @Test
    fun popOnRootArtifactVersionsGoesToArtifactView() {
        val nav = AppNavigator(Screen.ArtifactVersions(artifactId = "a-1"))
        nav.pop()
        assertEquals(Screen.ArtifactView("a-1"), nav.current)
    }

    @Test
    fun popOnRootHomeIsNoOp() {
        // 루트 화면(Home)은 논리적 상위가 없으므로 뒤로가기가 아무것도 안 한다.
        val nav = AppNavigator(Screen.Home)
        nav.pop()
        assertEquals(Screen.Home, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun parentOfMapsScreensCorrectly() {
        // parentOf 매핑이 화면별로 올바른 상위를 반환한다.
        assertEquals(Screen.ExperienceList, AppNavigator.parentOf(Screen.ExperienceEdit(null)))
        assertEquals(Screen.TargetList, AppNavigator.parentOf(Screen.TargetEdit(null)))
        assertEquals(Screen.TargetList, AppNavigator.parentOf(Screen.TargetDetail("t-1")))
        // 기존 목표 수정의 상위는 상세, 신규 생성의 상위는 목록.
        assertEquals(Screen.TargetDetail("t-1"), AppNavigator.parentOf(Screen.TargetEdit("t-1")))
        assertEquals(Screen.TemplateList, AppNavigator.parentOf(Screen.TemplateEdit(null)))
        assertEquals(Screen.TemplateList, AppNavigator.parentOf(Screen.TemplatePreset))
        assertEquals(Screen.TemplateList, AppNavigator.parentOf(Screen.TemplateInterpret))
        assertEquals(Screen.Home, AppNavigator.parentOf(Screen.Artifact()))
        assertEquals(Screen.Home, AppNavigator.parentOf(Screen.ArtifactList))
        assertEquals(Screen.ArtifactList, AppNavigator.parentOf(Screen.ArtifactView("a-1")))
        assertEquals(Screen.ArtifactView("a-1"), AppNavigator.parentOf(Screen.ArtifactVersions("a-1")))
        // 루트 화면은 null.
        assertNull(AppNavigator.parentOf(Screen.Home))
        assertNull(AppNavigator.parentOf(Screen.Session))
        assertNull(AppNavigator.parentOf(Screen.ExperienceList))
        assertNull(AppNavigator.parentOf(Screen.TargetList))
    }

    @Test
    fun popWithHistoryDelegatesToBackAndLetsSyncFromPathUpdateStack() {
        // CQ-1: history 가 있을 때 pop() 은 직접 스택을 줄이지 않고
        // BrowserHistory.back() 에 위임한다. 브라우저 popstate 가 오면
        // syncFromPath 가 스택을 갱신한다(이중 갱신 방지).
        //
        // BrowserHistory 는 expect class 라 commonTest 에서 직접 서브클래스화할 수 없다.
        // 대신 history=null(인메모리) 경로와 syncFromPath API 를 분리 검증한다:
        //
        // 1) history=null: pop() 이 즉시 스택을 pop.
        val navInMemory = AppNavigator(Screen.Home)
        navInMemory.push(Screen.ExperienceList)
        navInMemory.push(Screen.ExperienceEdit("a"))
        assertTrue(navInMemory.canGoBack)

        navInMemory.pop()
        assertEquals(Screen.ExperienceList, navInMemory.current)

        // 2) history 있을 때의 popstate 처리: syncFromPath 가 실제 스택 갱신을 담당함.
        //    이는 AppNavigator.syncFromPath 가 스택을 올바르게 줄이는지로 간접 검증한다.
        val navWeb = AppNavigator(Screen.ExperienceEdit("a"))
        navWeb.push(Screen.ExperienceEdit("b")) // 더 깊이 진입
        // 브라우저 popstate 가 /experiences/a 로 돌아왔다고 가정.
        navWeb.syncFromPath("/experiences/a")
        assertEquals(Screen.ExperienceEdit("a"), navWeb.current)
        // syncFromPath 가 /experiences/a 지점까지 스택을 되감았으므로 그 위 항목은 없어야 한다.
        // 시작 항목 하나만 남으면 canGoBack 은 false.
        assertFalse(navWeb.canGoBack)
    }
}
