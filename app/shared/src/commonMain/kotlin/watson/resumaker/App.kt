package watson.resumaker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import watson.resumaker.feature.artifact.ArtifactComingSoonScreen
import watson.resumaker.feature.auth.SessionScreen
import watson.resumaker.feature.auth.SessionViewModel
import watson.resumaker.feature.experience.ExperienceEditScreen
import watson.resumaker.feature.experience.ExperienceEditViewModel
import watson.resumaker.feature.experience.ExperienceListScreen
import watson.resumaker.feature.experience.ExperienceListViewModel
import watson.resumaker.feature.home.HomeScreen
import watson.resumaker.feature.home.HomeViewModel
import watson.resumaker.feature.mypage.MyPageScreen
import watson.resumaker.feature.mypage.MyPageViewModel
import watson.resumaker.feature.target.TargetEditScreen
import watson.resumaker.feature.target.TargetEditViewModel
import watson.resumaker.feature.target.TargetListScreen
import watson.resumaker.feature.target.TargetListViewModel
import watson.resumaker.feature.template.TemplateEditScreen
import watson.resumaker.feature.template.TemplateEditViewModel
import watson.resumaker.feature.template.TemplateListScreen
import watson.resumaker.feature.template.TemplateListViewModel
import watson.resumaker.navigation.AppNavigator
import watson.resumaker.navigation.BrowserHistory
import watson.resumaker.navigation.Routes
import watson.resumaker.navigation.Screen
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.theme.RmTheme

/**
 * 앱 루트. RmTheme로 감싸고, [AppNavigator] 상태에 따라 화면을 그린다(단방향: 화면은 내비 이벤트만 호출).
 * 세션 유무·브라우저 딥링크(WX-8)에 따라 시작 화면을 정한다.
 */
@Composable
fun App(container: AppContainer = remember { AppContainer() }) {
    RmTheme {
        val navigator = remember(container) {
            val history = BrowserHistory()
            val authed = container.session.currentUserId() != null
            // WX-8: 딥링크/새로고침 — 보관 세션이 있으면 현재 URL 경로를, 없으면 세션 화면을 시작 화면으로.
            val start = if (authed) {
                Routes.screenOf(history.currentPath()).takeIf { it != Screen.Session } ?: Screen.Home
            } else {
                Screen.Session
            }
            AppNavigator(start = start, history = history)
        }

        when (val screen = navigator.current) {
            Screen.Session -> {
                val vm = remember { SessionViewModel(container.accountApi, container.session) }
                SessionScreen(
                    viewModel = vm,
                    onAuthenticated = { navigator.switchRoot(Screen.Home) },
                )
            }

            Screen.Home -> {
                val vm = remember { HomeViewModel(container.experienceApi, container.targetApi, container.templateApi) }
                HomeScreen(
                    viewModel = vm,
                    onOpenExperiences = { navigator.switchRoot(Screen.ExperienceList) },
                    onOpenTargets = { navigator.switchRoot(Screen.TargetList) },
                    onOpenTemplates = { navigator.switchRoot(Screen.TemplateList) },
                    onOpenExperience = { navigator.push(Screen.ExperienceEdit(it)) },
                    onOpenTarget = { navigator.push(Screen.TargetEdit(it)) },
                    onOpenTemplate = { navigator.push(Screen.TemplateEdit(it)) },
                    onCreateExperience = { navigator.push(Screen.ExperienceEdit(null)) },
                    onOpenArtifact = { hasExperiences -> navigator.push(Screen.Artifact(hasExperiences)) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                    onSelectTab = { navigator.onHeaderTab(it) },
                )
            }

            Screen.ExperienceList -> {
                val vm = remember { ExperienceListViewModel(container.experienceApi) }
                ExperienceListScreen(
                    viewModel = vm,
                    selectedTab = HeaderTab.EXPERIENCE,
                    pendingMessage = navigator.consumePendingMessage(),
                    onCreate = { navigator.push(Screen.ExperienceEdit(null)) },
                    onOpen = { navigator.push(Screen.ExperienceEdit(it)) },
                    onSelectTab = { navigator.onHeaderTab(it) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                )
            }

            is Screen.ExperienceEdit -> {
                val vm = remember(screen.experienceId) {
                    ExperienceEditViewModel(container.experienceApi, screen.experienceId)
                }
                ExperienceEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    // WX-4: 저장 후 항상 경험 목록으로 + 성공 스낵바 1회.
                    onSaved = { navigator.returnToList(Screen.ExperienceList, "경험을 저장했어요") },
                )
            }

            Screen.TargetList -> {
                val vm = remember { TargetListViewModel(container.targetApi) }
                TargetListScreen(
                    viewModel = vm,
                    selectedTab = HeaderTab.TARGET,
                    pendingMessage = navigator.consumePendingMessage(),
                    onCreate = { navigator.push(Screen.TargetEdit(null)) },
                    onOpen = { navigator.push(Screen.TargetEdit(it)) },
                    onSelectTab = { navigator.onHeaderTab(it) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                )
            }

            is Screen.TargetEdit -> {
                val vm = remember(screen.targetId) {
                    TargetEditViewModel(container.targetApi, screen.targetId)
                }
                TargetEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    // WX-4: 저장 후 항상 목표 목록으로 + 성공 스낵바 1회.
                    onSaved = { navigator.returnToList(Screen.TargetList, "목표를 저장했어요") },
                )
            }

            Screen.TemplateList -> {
                val vm = remember { TemplateListViewModel(container.templateApi) }
                TemplateListScreen(
                    viewModel = vm,
                    selectedTab = HeaderTab.TEMPLATE,
                    pendingMessage = navigator.consumePendingMessage(),
                    onCreate = { navigator.push(Screen.TemplateEdit(null)) },
                    onOpen = { navigator.push(Screen.TemplateEdit(it)) },
                    onSelectTab = { navigator.onHeaderTab(it) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                )
            }

            is Screen.TemplateEdit -> {
                val vm = remember(screen.templateId) {
                    TemplateEditViewModel(container.templateApi, screen.templateId)
                }
                TemplateEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    // WX-4: 저장 후 항상 양식 목록으로 + 성공 스낵바 1회.
                    onSaved = { navigator.returnToList(Screen.TemplateList, "양식을 저장했어요") },
                )
            }

            is Screen.Artifact -> ArtifactComingSoonScreen(
                onBack = { navigator.pop() },
                onRecordExperience = { navigator.switchRoot(Screen.ExperienceList) },
                onAddTarget = { navigator.switchRoot(Screen.TargetList) },
                hasExperiences = screen.hasExperiences,
            )

            Screen.MyPage -> {
                val vm = remember { MyPageViewModel(container.accountApi, container.session) }
                MyPageScreen(
                    viewModel = vm,
                    onBack = { navigator.switchRoot(Screen.Home) },
                    onSignedOut = { navigator.resetToSession() },
                    onSelectTab = { navigator.onHeaderTab(it) },
                )
            }
        }
    }
}

/** 헤더 내비 탭(홈/경험/목표/양식) → 루트 화면 전환(WX-7). */
private fun AppNavigator.onHeaderTab(tab: HeaderTab) = when (tab) {
    HeaderTab.HOME -> switchRoot(Screen.Home)
    HeaderTab.EXPERIENCE -> switchRoot(Screen.ExperienceList)
    HeaderTab.TARGET -> switchRoot(Screen.TargetList)
    HeaderTab.TEMPLATE -> switchRoot(Screen.TemplateList)
}
