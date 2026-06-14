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
import watson.resumaker.navigation.AppNavigator
import watson.resumaker.navigation.Screen
import watson.resumaker.platform.copyToClipboard
import watson.resumaker.ui.component.RmTab
import watson.resumaker.ui.theme.RmTheme

/**
 * 앱 루트. RmTheme로 감싸고, [AppNavigator] 상태에 따라 화면을 그린다(단방향: 화면은 내비 이벤트만 호출).
 * 세션 유무에 따라 시작 화면(Home/Session)을 정한다.
 */
@Composable
fun App(container: AppContainer = remember { AppContainer() }) {
    RmTheme {
        val navigator = remember(container) {
            AppNavigator(if (container.session.currentUserId() != null) Screen.Home else Screen.Session)
        }

        when (val screen = navigator.current) {
            Screen.Session -> {
                val vm = remember { SessionViewModel(container.accountApi, container.session) }
                SessionScreen(
                    viewModel = vm,
                    onAuthenticated = { navigator.switchRoot(Screen.Home) },
                    onCopyUserId = { copyToClipboard(it) },
                )
            }

            Screen.Home -> {
                val vm = remember { HomeViewModel(container.experienceApi, container.targetApi) }
                HomeScreen(
                    viewModel = vm,
                    onOpenExperiences = { navigator.switchRoot(Screen.ExperienceList) },
                    onOpenTargets = { navigator.switchRoot(Screen.TargetList) },
                    onOpenExperience = { navigator.push(Screen.ExperienceEdit(it)) },
                    onOpenTarget = { navigator.push(Screen.TargetEdit(it)) },
                    onCreateExperience = { navigator.push(Screen.ExperienceEdit(null)) },
                    onOpenArtifact = { hasExperiences -> navigator.push(Screen.Artifact(hasExperiences)) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                    onTabSelect = { navigator.onTab(it) },
                )
            }

            Screen.ExperienceList -> {
                val vm = remember { ExperienceListViewModel(container.experienceApi) }
                ExperienceListScreen(
                    viewModel = vm,
                    onBack = { navigator.switchRoot(Screen.Home) },
                    onCreate = { navigator.push(Screen.ExperienceEdit(null)) },
                    onOpen = { navigator.push(Screen.ExperienceEdit(it)) },
                )
            }

            is Screen.ExperienceEdit -> {
                val vm = remember(screen.experienceId) {
                    ExperienceEditViewModel(container.experienceApi, screen.experienceId)
                }
                ExperienceEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    onSaved = { navigator.pop() },
                )
            }

            Screen.TargetList -> {
                val vm = remember { TargetListViewModel(container.targetApi) }
                TargetListScreen(
                    viewModel = vm,
                    onBack = { navigator.switchRoot(Screen.Home) },
                    onCreate = { navigator.push(Screen.TargetEdit(null)) },
                    onOpen = { navigator.push(Screen.TargetEdit(it)) },
                )
            }

            is Screen.TargetEdit -> {
                val vm = remember(screen.targetId) {
                    TargetEditViewModel(container.targetApi, screen.targetId)
                }
                TargetEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    onSaved = { navigator.pop() },
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
                    onTabSelect = { navigator.onTab(it) },
                    onCopyUserId = { copyToClipboard(it) },
                )
            }
        }
    }
}

/** 바텀 내비 탭 → 루트 화면 전환. */
private fun AppNavigator.onTab(tab: RmTab) = when (tab) {
    RmTab.HOME -> switchRoot(Screen.Home)
    RmTab.EXPERIENCE -> switchRoot(Screen.ExperienceList)
    RmTab.TARGET -> switchRoot(Screen.TargetList)
    RmTab.MY -> switchRoot(Screen.MyPage)
}
