package watson.resumaker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.feature.artifact.ArtifactCreateScreen
import watson.resumaker.feature.artifact.ArtifactCreateViewModel
import watson.resumaker.feature.artifact.ArtifactListScreen
import watson.resumaker.feature.artifact.ArtifactListViewModel
import watson.resumaker.feature.artifact.ArtifactScreen
import watson.resumaker.feature.artifact.ArtifactVersionsScreen
import watson.resumaker.feature.artifact.ArtifactVersionsViewModel
import watson.resumaker.feature.artifact.ArtifactViewModel
import watson.resumaker.feature.artifact.ad.AdDestination
import watson.resumaker.feature.artifact.ad.ConsoleAdMetricsReporter
import watson.resumaker.config.adsEnabled
import watson.resumaker.feature.artifact.quality.QualityImprovementScreen
import watson.resumaker.feature.artifact.quality.QualityReviewScreen
import watson.resumaker.feature.artifact.quality.QualityReviewViewModel
import watson.resumaker.feature.artifact.quality.QualityStep
import watson.resumaker.model.type.ArtifactKind
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
import watson.resumaker.feature.target.TargetDetailScreen
import watson.resumaker.feature.target.TargetDetailViewModel
import watson.resumaker.feature.target.TargetEditScreen
import watson.resumaker.feature.target.TargetEditViewModel
import watson.resumaker.feature.target.TargetListScreen
import watson.resumaker.feature.target.TargetListViewModel
import watson.resumaker.feature.template.TemplateEditScreen
import watson.resumaker.feature.template.TemplateEditViewModel
import watson.resumaker.feature.template.TemplateInterpretScreen
import watson.resumaker.feature.template.TemplateInterpretViewModel
import watson.resumaker.feature.template.TemplateListScreen
import watson.resumaker.feature.template.TemplateListViewModel
import watson.resumaker.feature.template.TemplatePresetScreen
import watson.resumaker.feature.template.TemplatePresetViewModel
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

        // 세션 만료(비자발적 401 → refresh 실패): 어느 화면에 있든 로그인 화면으로 리다이렉트한다.
        LaunchedEffect(container, navigator) {
            container.sessionExpirations.collect {
                if (navigator.current != Screen.Session) {
                    navigator.resetToSession()
                }
            }
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
                val vm = remember {
                    HomeViewModel(container.experienceApi, container.targetApi, container.templateApi, container.artifactApi)
                }
                HomeScreen(
                    viewModel = vm,
                    onOpenExperiences = { navigator.switchRoot(Screen.ExperienceList) },
                    onOpenTargets = { navigator.switchRoot(Screen.TargetList) },
                    onOpenTemplates = { navigator.switchRoot(Screen.TemplateList) },
                    onOpenExperience = { navigator.push(Screen.ExperienceEdit(it)) },
                    onOpenTarget = { navigator.push(Screen.TargetDetail(it)) },
                    onOpenTemplate = { navigator.push(Screen.TemplateEdit(it)) },
                    onCreateExperience = { navigator.push(Screen.ExperienceEdit(null)) },
                    onOpenArtifact = { hasExperiences -> navigator.push(Screen.Artifact(hasExperiences)) },
                    onOpenArtifactList = { navigator.push(Screen.ArtifactList) },
                    onOpenArtifactView = { artifactId -> navigator.push(Screen.ArtifactView(artifactId = artifactId)) },
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
                    onOpen = { navigator.push(Screen.TargetDetail(it)) },
                    onSelectTab = { navigator.onHeaderTab(it) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                )
            }

            is Screen.TargetDetail -> {
                val vm = remember(screen.targetId) {
                    TargetDetailViewModel(container.targetApi, screen.targetId)
                }
                TargetDetailScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    onEdit = { navigator.push(Screen.TargetEdit(screen.targetId)) },
                    onCreateArtifact = { navigator.push(Screen.Artifact()) },
                )
            }

            is Screen.TargetEdit -> {
                val vm = remember(screen.targetId) {
                    TargetEditViewModel(container.targetApi, screen.targetId)
                }
                TargetEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    // WX-4: 저장 후 항상 목표 목록으로 + 성공 스낵바 1회(생성/수정·전략 유무로 분기한 문구).
                    onSaved = { message -> navigator.returnToList(Screen.TargetList, message) },
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
                    onStartFromPreset = { navigator.push(Screen.TemplatePreset) },
                    onStartFromPaste = { navigator.push(Screen.TemplateInterpret) },
                )
            }

            is Screen.TemplateEdit -> {
                val vm = remember(screen.templateId, screen.presetName) {
                    val presetRows = screen.presetSections?.mapIndexed { i, s ->
                        watson.resumaker.feature.template.SectionRow(
                            key = i,
                            name = s.name,
                            character = s.character,
                            required = s.required,
                        )
                    }
                    TemplateEditViewModel(
                        templateApi = container.templateApi,
                        templateId = screen.templateId,
                        presetName = screen.presetName,
                        presetSections = presetRows,
                    )
                }
                TemplateEditScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    // WX-4: 저장 후 항상 양식 목록으로 + 성공 스낵바 1회.
                    onSaved = { navigator.returnToList(Screen.TemplateList, "양식을 저장했어요") },
                )
            }

            Screen.TemplatePreset -> {
                val vm = remember { TemplatePresetViewModel(container.templatePresetApi) }
                TemplatePresetScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    onPresetSelected = { preset ->
                        // L-3: pop→push 이중 호출 대신 원자적 replaceTop으로 history 중간 URL 노출 방지.
                        navigator.replaceTop(
                            Screen.TemplateEdit(
                                templateId = null,
                                presetName = preset.name,
                                presetSections = preset.sections,
                            ),
                        )
                    },
                    onStartFromEdit = {
                        navigator.replaceTop(Screen.TemplateEdit(null))
                    },
                )
            }

            Screen.TemplateInterpret -> {
                val vm = remember { TemplateInterpretViewModel(container.templateInterpretApi) }
                TemplateInterpretScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    onConfirmed = { name, sections ->
                        // L-3: 원자적 replaceTop으로 history 중간 URL 노출 방지.
                        navigator.replaceTop(
                            Screen.TemplateEdit(
                                templateId = null,
                                presetName = name,
                                presetSections = sections,
                            ),
                        )
                    },
                    onFallbackToPreset = {
                        navigator.replaceTop(Screen.TemplatePreset)
                    },
                    onFallbackToEdit = {
                        navigator.replaceTop(Screen.TemplateEdit(null))
                    },
                )
            }

            is Screen.Artifact -> {
                val vm = remember(screen) {
                    ArtifactCreateViewModel(
                        artifactApi = container.artifactApi,
                        experienceApi = container.experienceApi,
                        targetApi = container.targetApi,
                        templateApi = container.templateApi,
                        // EDIT_INPUTS 재시도면 실패 작업의 입력을 미리 채운다(없으면 빈 폼).
                        prefillJob = screen.prefillJob,
                    )
                }
                ArtifactCreateScreen(
                    viewModel = vm,
                    selectedTab = HeaderTab.CREATE,
                    onSelectTab = { navigator.onHeaderTab(it) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                    onBack = { navigator.pop() },
                    onSubmitted = {
                        // 제출(202) 성공: 생성 진입을 닫고 산출물 목록으로 전환한다. 방금 제출한 작업이 목록 상단에
                        // "생성 중"으로 보이고, 목록의 폴링으로 완료되면 완성 산출물로 전환된다.
                        navigator.replaceTop(Screen.ArtifactList)
                    },
                    onRecordExperience = { navigator.switchRoot(Screen.ExperienceList) },
                    onAddTarget = { navigator.switchRoot(Screen.TargetList) },
                )
            }

            Screen.ArtifactList -> {
                val vm = remember { ArtifactListViewModel(container.artifactApi) }
                val adMetrics = remember { ConsoleAdMetricsReporter() }
                ArtifactListScreen(
                    viewModel = vm,
                    selectedTab = HeaderTab.ARTIFACT,
                    onSelectTab = { navigator.onHeaderTab(it) },
                    onOpenMyPage = { navigator.switchRoot(Screen.MyPage) },
                    onBack = { navigator.pop() },
                    onOpenArtifact = { artifactId -> navigator.push(Screen.ArtifactView(artifactId = artifactId)) },
                    onCreate = { navigator.push(Screen.Artifact()) },
                    // 입력 관련 실패 '경험 다시 고르기' → 실패 작업 입력을 프리필한 생성 화면.
                    onEditInputs = { job -> navigator.push(Screen.Artifact(prefillJob = job)) },
                    // 대기 시간 광고 슬롯 CTA → 앱 내 다음 행동으로 이동. 목표 추가는 push(편집)로, 나머지는 탭 전환.
                    onAdNavigate = { dest ->
                        when (dest) {
                            AdDestination.EXPERIENCE_LIST -> navigator.switchRoot(Screen.ExperienceList)
                            AdDestination.TEMPLATE_LIST -> navigator.switchRoot(Screen.TemplateList)
                            AdDestination.TARGET_CREATE -> navigator.push(Screen.TargetEdit(null))
                        }
                    },
                    onAdImpression = { adMetrics.onImpression(it) },
                    onAdClick = { adMetrics.onClick(it) },
                    adsEnabled = adsEnabled(),
                )
            }

            is Screen.ArtifactView -> {
                val vm = remember(screen.artifactId) {
                    ArtifactViewModel(
                        artifactApi = container.artifactApi,
                        qualityApi = container.qualityApi,
                        artifactId = screen.artifactId,
                        initial = screen.initial,
                    )
                }
                ArtifactScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                    onViewVersions = { navigator.push(Screen.ArtifactVersions(screen.artifactId)) },
                    onStartQualityReview = { navigator.push(Screen.ArtifactQualityReview(screen.artifactId)) },
                    // 비차단 개선 진행 카드의 "확인하기": 준비된 작업 id를 실어 후보 비교·채택(2단계)으로 곧장 재진입(§3).
                    onConfirmQualityImprovement = { jobId ->
                        navigator.push(Screen.ArtifactQualityReview(screen.artifactId, resumeJobId = jobId))
                    },
                )
            }

            is Screen.ArtifactQualityReview -> {
                // ViewModel을 단일 인스턴스로 공유: 1단계(QualityReviewScreen)와 2단계(QualityImprovementScreen)가
                // 같은 인스턴스를 통해 상태를 이어받는다. artifactKind는 RESUME 전용 진입점(QC10)이 보장하므로 고정.
                val vm = remember(screen.artifactId, screen.resumeJobId) {
                    QualityReviewViewModel(
                        qualityApi = container.qualityApi,
                        artifactId = screen.artifactId,
                        artifactKind = ArtifactKind.RESUME,
                        // non-null이면 점검을 건너뛰고 이 작업의 후보 비교·채택(2단계)으로 곧장 진입(§3 비차단 재진입).
                        resumeJobId = screen.resumeJobId,
                    )
                }
                // 단계 기반 렌더링: CANDIDATES 단계까지는 1단계, 이후는 2단계를 보여준다.
                // LaunchedEffect 대신 state.step을 직접 읽어 Compose recomposition으로 자연스럽게 전환한다.
                val qualityState by vm.state.collectAsStateWithLifecycle()
                if (qualityState.step == QualityStep.CANDIDATES || qualityState.step == QualityStep.ADOPTED) {
                    QualityImprovementScreen(
                        viewModel = vm,
                        onBack = { navigator.pop() },
                        onAdopted = { navigator.pop() },
                    )
                } else {
                    QualityReviewScreen(
                        viewModel = vm,
                        onBack = { navigator.pop() },
                        onProceedToImprovement = { /* state.step 전환이 recomposition을 트리거한다 */ },
                        // 비차단 접수(202): 산출물 열람 화면으로 복귀한다. 복귀하면 열람 화면이 진행 카드로 완료까지
                        // 폴링한다(§3 — 빈 화면 대기 해소). 열람 화면 VM은 pop 후 재생성돼 최신 작업을 자동 복원한다.
                        onImprovementSubmitted = { navigator.pop() },
                        onOpenExperience = { experienceId ->
                            if (experienceId != null) navigator.push(Screen.ExperienceEdit(experienceId))
                            else navigator.push(Screen.ExperienceList)
                        },
                    )
                }
            }

            is Screen.ArtifactVersions -> {
                val vm = remember(screen.artifactId) {
                    ArtifactVersionsViewModel(
                        artifactApi = container.artifactApi,
                        artifactId = screen.artifactId,
                    )
                }
                ArtifactVersionsScreen(
                    viewModel = vm,
                    onBack = { navigator.pop() },
                )
            }

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

/** 헤더 내비 탭(홈/경험/목표/양식/만들기/산출물) → 루트 화면 전환(WX-7). */
private fun AppNavigator.onHeaderTab(tab: HeaderTab) = when (tab) {
    HeaderTab.HOME -> switchRoot(Screen.Home)
    HeaderTab.EXPERIENCE -> switchRoot(Screen.ExperienceList)
    HeaderTab.TARGET -> switchRoot(Screen.TargetList)
    HeaderTab.TEMPLATE -> switchRoot(Screen.TemplateList)
    HeaderTab.CREATE -> switchRoot(Screen.Artifact())
    HeaderTab.ARTIFACT -> switchRoot(Screen.ArtifactList)
}
