package watson.resumaker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.feature.experience.formatPeriod
import watson.resumaker.feature.target.targetTitle
import watson.resumaker.feature.template.templateSummary
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ExperienceIconChip
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.InlineAddButton
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.LocalContentMaxWidth
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.TextLink
import watson.resumaker.ui.component.TypeBadge
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.gridColumnsFor
import watson.resumaker.ui.theme.pagePadding

/**
 * 디자인 시스템 §8.2 홈 대시보드(웹). 전체폭 헤더 + 1120dp 콘텐츠.
 * 경험·목표를 반응형 카드 그리드로, 섹션 헤더에 인라인 "추가" 버튼(WX-5/6/13).
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenExperiences: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenExperience: (String) -> Unit,
    onOpenTarget: (String) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onCreateExperience: () -> Unit,
    onOpenArtifact: (hasExperiences: Boolean) -> Unit,
    onSelectTab: (HeaderTab) -> Unit,
    onOpenMyPage: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.WIDE,
        header = { windowSize ->
            AppHeader(
                selected = HeaderTab.HOME,
                onSelectTab = onSelectTab,
                onOpenAccount = onOpenMyPage,
                windowSize = windowSize,
                horizontalPadding = windowSize.pagePadding(),
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> Column(
                modifier = contentModifier
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space8),
            ) {
                SkeletonList(count = 2, showLeadingChip = true)
                SkeletonList(count = 2, showLeadingChip = false)
            }
            else -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space8),
            ) {
                if (state.errorMessage != null) {
                    ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
                }

                val columns = gridColumnsFor(windowSize, LocalContentMaxWidth.current)
                val hasExperiences = state.experiences.isNotEmpty()
                val hasTargets = state.targets.isNotEmpty()

                // #8 핵심 CTA — 이력서·포트폴리오 만들기를 최상단 hero 영역에 배치.
                // 경험이 있으면 바로 만들기 진입, 없으면 경험 기록 유도.
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                    Text(text = "이력서·포트폴리오 만들기", style = RmTextStyles.headingM, color = colors.textPrimary)
                    if (hasExperiences && hasTargets) {
                        // 준비 완료 — 바로 만들기 CTA
                        PrimaryButton(
                            text = "만들기 시작",
                            onClick = { onOpenArtifact(true) },
                        )
                    } else {
                        // #9 온보딩 사전작업 체크리스트 — 경험·목표가 모두 채워지기 전까지 표시
                        OnboardingChecklist(
                            hasExperiences = hasExperiences,
                            hasTargets = hasTargets,
                            onCreateExperience = onCreateExperience,
                            onOpenTargets = onOpenTargets,
                        )
                    }
                }

                // 내 경험
                SectionHeader(
                    title = "내 경험",
                    onViewAll = onOpenExperiences.takeIf { hasExperiences },
                    onAdd = onCreateExperience,
                )
                if (state.experiencePreview.isEmpty()) {
                    EmptyState(
                        icon = RmIcons.Note,
                        title = "아직 기록한 경험이 없어요",
                        description = "첫 경험을 기록해 볼까요?",
                        actionText = "경험 기록하기",
                        onAction = onCreateExperience,
                    )
                } else {
                    CardGrid(columns = columns, itemCount = state.experiencePreview.size) {
                        items(state.experiencePreview, key = { it.id }) { e ->
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
                SectionHeader(
                    title = "내 목표",
                    onViewAll = onOpenTargets.takeIf { hasTargets },
                    onAdd = onOpenTargets,
                    addText = "추가",
                )
                if (state.targetPreview.isEmpty()) {
                    EmptyState(
                        icon = RmIcons.Target,
                        title = "아직 등록한 목표가 없어요",
                        description = "어떤 회사·직무를 겨냥하나요?",
                        actionText = "목표 추가하기",
                        onAction = onOpenTargets,
                    )
                } else {
                    CardGrid(columns = columns, itemCount = state.targetPreview.size) {
                        items(state.targetPreview, key = { it.id }) { t ->
                            ListItemCard(
                                title = targetTitle(t),
                                meta = t.recruitDirection,
                                onClick = { onOpenTarget(t.id) },
                            )
                        }
                    }
                }

                // 내 양식 — #10 선택사항 안내를 description에 포함
                SectionHeader(
                    title = "내 양식",
                    onViewAll = onOpenTemplates.takeIf { state.templates.isNotEmpty() },
                    onAdd = onOpenTemplates,
                    addText = "추가",
                )
                if (state.templatePreview.isEmpty()) {
                    EmptyState(
                        icon = RmIcons.Note,
                        title = "아직 만든 양식이 없어요",
                        // #10 양식은 선택사항임을 명확히 안내
                        description = "양식은 선택이에요. AI가 자동으로 만들어 주니 없어도 이력서를 바로 생성할 수 있어요.",
                        actionText = "양식 만들기",
                        onAction = onOpenTemplates,
                    )
                } else {
                    CardGrid(columns = columns, itemCount = state.templatePreview.size) {
                        items(state.templatePreview, key = { it.id }) { t ->
                            ListItemCard(
                                title = t.name,
                                meta = templateSummary(t),
                                onClick = { onOpenTemplate(t.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(RmSpacing.space2))
            }
        }
    }
}

/**
 * #9 온보딩 사전작업 체크리스트. 경험·목표 중 하나라도 미완료인 동안 홈 상단에 표시한다.
 * 양식은 선택사항이므로 체크리스트에 포함하지 않는다(#10).
 * 각 항목은 완료 여부를 아이콘으로 구분하고, 미완료 항목에는 바로가기 링크를 제공한다.
 */
@Composable
private fun OnboardingChecklist(
    hasExperiences: Boolean,
    hasTargets: Boolean,
    onCreateExperience: () -> Unit,
    onOpenTargets: () -> Unit,
) {
    val colors = RmTheme.colors
    InfoCard(icon = RmIcons.Sparkles, title = "이력서를 만들려면 먼저 준비해요") {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
            OnboardingCheckItem(
                label = "경험 기록하기",
                description = "내가 한 일을 한 줄이라도 남겨두세요",
                done = hasExperiences,
                onAction = onCreateExperience.takeUnless { hasExperiences },
                actionText = "기록하기",
            )
            OnboardingCheckItem(
                label = "목표 추가하기",
                description = "어떤 회사·직무를 겨냥하는지 알려주세요",
                done = hasTargets,
                onAction = onOpenTargets.takeUnless { hasTargets },
                actionText = "추가하기",
            )
            // #10 양식은 선택사항임을 체크리스트 내부에서도 명시
            OnboardingCheckItem(
                label = "양식 설정 (선택)",
                description = "없어도 괜찮아요. AI가 자동으로 만들어 줘요.",
                done = true, // 항상 완료로 표시 — 선택사항이므로 강요하지 않음
                onAction = null,
                actionText = null,
            )
        }
    }
}

@Composable
private fun OnboardingCheckItem(
    label: String,
    description: String,
    done: Boolean,
    onAction: (() -> Unit)?,
    actionText: String?,
) {
    val colors = RmTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        // 완료 아이콘(초록 체크) vs 미완료 아이콘(회색 원)
        Icon(
            imageVector = if (done) RmIcons.CheckCircle else RmIcons.Circle,
            contentDescription = if (done) "완료" else "미완료",
            tint = if (done) colors.success else colors.textTertiary,
            modifier = Modifier.size(RmSize.iconMd),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = RmTextStyles.bodyS.copy(fontWeight = FontWeight.SemiBold),
                color = if (done) colors.textSecondary else colors.onPrimaryContainer,
            )
            Text(
                text = description,
                style = RmTextStyles.caption,
                color = colors.onPrimaryContainer,
            )
        }
        if (onAction != null && actionText != null) {
            TextLink(text = actionText, onClick = onAction)
        }
    }
}

/**
 * 반응형 카드 그리드(WX-5). 홈은 외부 verticalScroll 안이라 그리드 자체 스크롤을 끄고 높이를 내용에 맞춘다.
 * 행 수 기반 최대 높이를 두어 무한 높이 측정 충돌을 피한다.
 */
@Composable
private fun CardGrid(
    columns: Int,
    itemCount: Int,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    val rows = ((itemCount + columns - 1) / columns).coerceAtLeast(1)
    // 카드 1개 대략 높이(아이콘칩 48 + 패딩) 상한으로 높이 제한 — 미리보기는 소량이라 충분.
    val maxHeight = (RmSize.skeletonItemHeight.value * rows + RmSpacing.space3.value * (rows - 1) + 24f)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = androidx.compose.ui.unit.Dp(maxHeight + RmSpacing.space3.value)),
        contentPadding = PaddingValues(bottom = RmSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
        userScrollEnabled = false,
        content = content,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    onViewAll: (() -> Unit)?,
    onAdd: () -> Unit,
    addText: String = "기록하기",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
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
        InlineAddButton(text = addText, onClick = onAdd)
    }
}
