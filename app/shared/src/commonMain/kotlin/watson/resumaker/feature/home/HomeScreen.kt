package watson.resumaker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.feature.experience.formatPeriod
import watson.resumaker.feature.target.targetTitle
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.BottomActionBar
import watson.resumaker.ui.component.BrandTopBar
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ExperienceIconChip
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmBottomNav
import watson.resumaker.ui.component.RmTab
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.StatusBadge
import watson.resumaker.ui.component.TextLink
import watson.resumaker.ui.component.TypeBadge
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmMotion
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §8.2 홈 대시보드. 경험·목표 미리보기 + 산출물 ComingSoon 진입.
 * 하단 바텀내비(홈 활성) + 하단 고정 "새 경험 기록하기".
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenExperiences: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenExperience: (String) -> Unit,
    onOpenTarget: (String) -> Unit,
    onCreateExperience: () -> Unit,
    onOpenArtifact: (hasExperiences: Boolean) -> Unit,
    onTabSelect: (RmTab) -> Unit,
    onOpenMyPage: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors

    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = false,
        topBar = {
            BrandTopBar(actionContent = {
                IconButton(onClick = onOpenMyPage) {
                    Icon(RmIcons.Person, contentDescription = "마이페이지", tint = colors.textSecondary)
                }
            })
        },
        bottomBar = { RmBottomNav(selected = RmTab.HOME, onSelect = onTabSelect) },
        floatingBottom = {
            BottomActionBar {
                PrimaryButton(
                    text = "새 경험 기록하기",
                    onClick = onCreateExperience,
                    pressedScale = RmMotion.pressScaleStrong,
                )
            }
        },
    ) { contentModifier ->
        when {
            state.loading -> Column(
                modifier = contentModifier
                    .padding(horizontal = RmSpacing.contentPadding)
                    .padding(top = RmSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space8),
            ) {
                // UX-3: 홈 진입 로딩도 스켈레톤으로 레이아웃 점프 방지(경험·목표 섹션 모사).
                SkeletonList(count = 2, showLeadingChip = true)
                SkeletonList(count = 2, showLeadingChip = false)
            }
            else -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RmSpacing.contentPadding)
                    .padding(top = RmSpacing.space4, bottom = RmSpacing.space10 + RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space8),
            ) {
                if (state.errorMessage != null) {
                    ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
                }

                // 내 경험
                SectionHeader(title = "내 경험", onViewAll = onOpenExperiences.takeIf { state.experiences.isNotEmpty() })
                if (state.experiencePreview.isEmpty()) {
                    EmptyState(
                        icon = RmIcons.Note,
                        title = "아직 기록한 경험이 없어요",
                        description = "첫 경험을 기록해 볼까요?",
                        actionText = "경험 기록하기",
                        onAction = onCreateExperience,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                        state.experiencePreview.forEach { e ->
                            ListItemCard(
                                title = e.title,
                                meta = formatPeriod(e.periodStart, e.periodEnd),
                                leading = { ExperienceIconChip(e.type) },
                                badge = { TypeBadge(e.type) },
                                onClick = { onOpenExperience(e.id) },
                            )
                        }
                    }
                }

                // 내 목표
                SectionHeader(title = "내 목표", onViewAll = onOpenTargets.takeIf { state.targets.isNotEmpty() })
                if (state.targetPreview.isEmpty()) {
                    EmptyState(
                        icon = RmIcons.Target,
                        title = "아직 등록한 목표가 없어요",
                        description = "어떤 회사·직무를 겨냥하나요?",
                        actionText = "목표 추가하기",
                        onAction = onOpenTargets,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                        state.targetPreview.forEach { t ->
                            ListItemCard(
                                title = targetTitle(t),
                                meta = t.recruitDirection,
                                onClick = { onOpenTarget(t.id) },
                            )
                        }
                    }
                }

                // 이력서·포트폴리오 (준비 중). 빈 경험묶음이면 "먼저 경험 기록" 예방형 카피로 분기(수용기준 8).
                val hasExperiences = state.experiences.isNotEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                        Text(text = "이력서·포트폴리오", style = RmTextStyles.headingM, color = colors.textPrimary)
                        StatusBadge(text = "준비 중")
                    }
                    InfoCard(
                        icon = RmIcons.Sparkles,
                        title = if (hasExperiences) "곧 제공됩니다" else "먼저 경험을 기록해 주세요",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                            Text(
                                text = if (hasExperiences) {
                                    "경험과 목표를 충실히 쌓아두면, 준비되는 대로 최적의 이력서·포트폴리오를 만들어 드려요."
                                } else {
                                    "이력서·포트폴리오는 기록한 경험을 재료로 만들어요. 먼저 한 가지라도 기록해 두면 준비가 빨라져요."
                                },
                                style = RmTextStyles.bodyS,
                                color = colors.onPrimaryContainer,
                            )
                            TextLink(text = "준비 현황 보기", onClick = { onOpenArtifact(hasExperiences) })
                        }
                    }
                }
                Spacer(Modifier.height(RmSpacing.space2))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onViewAll: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = RmTextStyles.headingM,
            color = RmTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (onViewAll != null) {
            TextLink(text = "전체보기", onClick = onViewAll)
        }
    }
}
