package watson.resumaker.feature.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.StrategyStatus
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ComingSoon
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.headerWidthForTab
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.SecondaryButton
import watson.resumaker.ui.component.SegmentedToggle
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.StatusBadge
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 산출물 생성 진입 화면. 종류 선택 → 경험 다중 선택(필수) → 목표 선택(필수) → (이력서) 양식 선택(필수) → 제출.
 * 빈 경험묶음이면 ComingSoon 예방형 카피로 분기한다(수용 기준 8). 제출(202)은 빠르며, 완료 확인은 산출물
 * 목록의 폴링이 담당한다 — 제출 성공 시 [onSubmitted]로 목록으로 이동한다.
 */
@Composable
fun ArtifactCreateScreen(
    viewModel: ArtifactCreateViewModel,
    /** non-null이면 탭 목적지(switchRoot)로 진입한 것이므로 AppHeader(탭)를, null이면 PageHeader(뒤로가기)를 그린다. */
    selectedTab: HeaderTab? = null,
    onBack: () -> Unit,
    onSelectTab: (HeaderTab) -> Unit = {},
    onOpenMyPage: () -> Unit = {},
    /** 제출(202) 성공 시 1회 호출. 호출자가 산출물 목록(Screen.ArtifactList)으로 이동한다. */
    onSubmitted: () -> Unit,
    onRecordExperience: () -> Unit,
    onAddTarget: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            viewModel.consumeSubmitted()
            onSubmitted()
        }
    }

    AppScaffold(
        contentWidth = ContentWidth.NARROW,               // 폼 가독폭(760) 유지
        headerWidth = headerWidthForTab(selectedTab),    // 탭 목적지 헤더 폭 정책(공유 크롬 일치)
        header = { windowSize ->
            if (selectedTab != null) {
                AppHeader(
                    selected = selectedTab,
                    onSelectTab = onSelectTab,
                    onOpenAccount = onOpenMyPage,
                    windowSize = windowSize,
                    horizontalPadding = windowSize.pagePadding(),
                )
            } else {
                PageHeader(
                    title = "이력서·포트폴리오 만들기",
                    horizontalPadding = windowSize.pagePadding(),
                    onBack = onBack,
                )
            }
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                SkeletonList(showLeadingChip = false)
            }
            state.errorMessage != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
            }
            // 빈 경험묶음 예방형 분기(도메인 §110·§410): 산출물은 경험을 재료로 만든다.
            state.hasNoExperiences -> Box(
                modifier = contentModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ComingSoon(
                    onRecordExperience = onRecordExperience,
                    onAddTarget = onAddTarget,
                    hasExperiences = false,
                )
            }
            else -> CreateForm(
                state = state,
                contentModifier = contentModifier,
                horizontalPadding = pad,
                onSelectKind = viewModel::selectKind,
                onToggleExperience = viewModel::toggleExperience,
                onSelectTarget = viewModel::selectTarget,
                onSelectTemplate = viewModel::selectTemplate,
                onSelectAiTemplate = viewModel::selectAiTemplate,
                onGenerate = viewModel::generate,
                onDismissError = viewModel::dismissGenerationError,
                onRetryGenerate = viewModel::retryGenerate,
                onAddTarget = onAddTarget,
            )
        }
    }
}

@Composable
private fun CreateForm(
    state: ArtifactCreateUiState,
    contentModifier: Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onSelectKind: (ArtifactKind) -> Unit,
    onToggleExperience: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onSelectAiTemplate: () -> Unit,
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
    /** 생성 실패 후 직전 선택 그대로 API를 재호출한다(#4). 한도 초과가 아닌 실패에만 배선. */
    onRetryGenerate: () -> Unit,
    /** 목표가 없을 때 막다른 길을 막기 위한 "목표 추가하기" 이동(UX-02). */
    onAddTarget: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = contentModifier
            .verticalScroll(scroll)
            .padding(horizontal = horizontalPadding)
            .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space6),
    ) {
        SectionLabel("무엇을 만들까요?")
        SegmentedToggle(
            options = listOf("이력서", "포트폴리오"),
            selectedIndex = if (state.kind == ArtifactKind.RESUME) 0 else 1,
            onSelect = { onSelectKind(if (it == 0) ArtifactKind.RESUME else ArtifactKind.PORTFOLIO) },
        )

        SectionLabel("어떤 경험을 담을까요? (1개 이상)")
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
            state.experiences.forEach { exp ->
                ExperienceSelectRow(
                    experience = exp,
                    selected = exp.id in state.selectedExperienceIds,
                    onToggle = { onToggleExperience(exp.id) },
                )
            }
        }

        SectionLabel("어디에 지원하나요?")
        if (state.targets.isEmpty()) {
            // 막다른 길 금지(UX-02): 안내만 하지 않고 곧장 목표를 추가하러 갈 수 있게 한다.
            EmptyHint("아직 목표가 없어요. 먼저 지원할 목표를 추가해 주세요.")
            SecondaryButton(text = "목표 추가하기", onClick = onAddTarget)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                state.targets.forEach { target ->
                    TargetSelectRow(
                        target = target,
                        selected = state.selectedTargetId == target.id,
                        onSelect = { onSelectTarget(target.id) },
                    )
                }
            }
        }

        if (state.templateStepVisible) {
            SectionLabel("어떤 양식을 쓸까요?")
            Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                // "양식 자동" — 양식을 고르지 않아도 AI가 경험·목표로 구조를 정한다(서버 §178). 항상 첫 선택지로 노출.
                AiTemplateSelectRow(
                    selected = state.useAiTemplate,
                    onSelect = onSelectAiTemplate,
                )
                if (state.templates.isEmpty()) {
                    EmptyHint("저장한 양식이 없어요. 위 '양식 자동'으로 만들거나, 먼저 이력서 양식을 만들어 주세요.")
                } else {
                    state.templates.forEach { template ->
                        TemplateSelectRow(
                            template = template,
                            selected = state.selectedTemplateId == template.id,
                            onSelect = { onSelectTemplate(template.id) },
                        )
                    }
                }
            }
        }

        if (state.generationError != null) {
            // 한도 초과(429)는 재시도해도 같은 오류가 나므로 닫기만 제공하고 재요청하지 않는다.
            // 그 외 실패(AI_GENERATION_UNAVAILABLE 등)는 onRetry로 직전 생성을 실제 재요청한다(#4).
            ErrorBanner(
                message = state.generationError,
                onRetry = if (state.isGenerationQuotaExceeded) onDismissError else onRetryGenerate,
                title = if (state.isGenerationQuotaExceeded) "오늘은 더 만들 수 없어요" else "생성하지 못했어요",
            )
        }

        PrimaryButton(
            text = if (state.generating) "시작하는 중…" else "만들기",
            onClick = onGenerate,
            enabled = state.canSubmit,
            loading = state.generating,
        )
        when {
            state.generating -> Text(
                text = "생성을 시작하고 있어요. 잠시 후 목록에서 진행 상황을 확인할 수 있어요.",
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
            )
            // 비활성 사유를 구체적으로 안내(UX-04 — 무엇이 부족한지 모르는 막연한 비활성 제거).
            state.submitBlockReason != null -> Text(
                text = state.submitBlockReason!!,
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
            )
            state.selectedTargetStrategyNotReady -> Text(
                // 선택한 목표 전략이 아직 준비되지 않아도 생성은 가능하다 — 공고 원문 기반으로 만든다는 안내.
                text = "선택한 목표의 전략이 아직 준비되지 않았어요. 공고 원문을 바탕으로 만들어 드릴게요.",
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = RmTextStyles.label, color = RmTheme.colors.textPrimary)
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, style = RmTextStyles.bodyS, color = RmTheme.colors.textSecondary)
}

@Composable
private fun ExperienceSelectRow(
    experience: ExperienceResponse,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    SelectableRow(selected = selected, onClick = onToggle) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = experience.title,
                style = RmTextStyles.bodyM,
                color = RmTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = experience.body,
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = RmSpacing.space1),
            )
        }
    }
}

@Composable
private fun TargetSelectRow(
    target: TargetResponse,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    SelectableRow(selected = selected, onClick = onSelect) {
        Column(modifier = Modifier.weight(1f)) {
            // 카드의 정체성은 '어디에(회사)·무엇으로(직무) 지원하는가'다. 회사·직무를 제목(진한 bodyM)으로 올리고
            // 채용 방향(서술 문장)은 보조 본문으로 둔다. 회사·직무가 비어 있으면 채용 방향을 제목으로 끌어올린다.
            val heading = listOfNotNull(target.companyName, target.jobTitle).joinToString(" · ")
                .ifBlank { target.recruitDirection }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heading,
                    style = RmTextStyles.bodyM,
                    color = RmTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 제목 라인 우측 작은 전략 상태 배지(완료/분석 중만; 실패·없음은 배지 없음).
                TargetStrategyBadgeInline(target.strategyStatus)
            }
            // 채용 방향을 제목으로 올린 경우(회사·직무 없음)엔 중복 표시하지 않는다.
            if (heading != target.recruitDirection) {
                Text(
                    text = target.recruitDirection,
                    style = RmTextStyles.caption,
                    color = RmTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = RmSpacing.space1),
                )
            }
        }
    }
}

/**
 * 목표 선택 행 제목 우측 작은 전략 상태 배지. 완료(success)·분석 중(warning)만 표시하고,
 * 실패·없음(FAILED) 등 그 외 상태는 배지를 그리지 않는다(전략 없어도 생성은 항상 가능 — 막다른 길 금지).
 */
@Composable
private fun TargetStrategyBadgeInline(status: StrategyStatus) {
    val colors = RmTheme.colors
    when (status) {
        StrategyStatus.READY -> StatusBadge(
            text = "전략 완료",
            fg = colors.success,
            bg = colors.successBg,
            modifier = Modifier.padding(start = RmSpacing.space2),
        )
        StrategyStatus.PENDING, StrategyStatus.EXTRACTING -> StatusBadge(
            text = "분석 중",
            fg = colors.warning,
            bg = colors.warningBg,
            modifier = Modifier.padding(start = RmSpacing.space2),
        )
        StrategyStatus.FAILED -> Unit
    }
}

/**
 * "양식 자동(AI에 맡기기)" 선택 행. 고르면 양식을 지정하지 않고 생성해 AI가 경험·목표로 섹션 구조를 정한다.
 */
@Composable
private fun AiTemplateSelectRow(
    selected: Boolean,
    onSelect: () -> Unit,
) {
    SelectableRow(selected = selected, onClick = onSelect) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "양식 자동 (AI에 맡기기)",
                style = RmTextStyles.bodyM,
                color = RmTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "경험과 목표에 맞는 섹션 구조를 AI가 정해요.",
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = RmSpacing.space1),
            )
        }
    }
}

@Composable
private fun TemplateSelectRow(
    template: TemplateResponse,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    SelectableRow(selected = selected, onClick = onSelect) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.name,
                style = RmTextStyles.bodyM,
                color = RmTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "섹션 ${template.sections.size}개",
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
                modifier = Modifier.padding(top = RmSpacing.space1),
            )
        }
    }
}

/**
 * 선택 가능한 행. 선택 시 primary 테두리·배경 강조, 우측에 선택 표식(점).
 */
@Composable
private fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val colors = RmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) colors.primaryContainer else colors.surface,
                RoundedCornerShape(RmRadius.card),
            )
            .border(
                RmSize.hairline,
                if (selected) colors.primaryBorder else colors.borderSubtle,
                RoundedCornerShape(RmRadius.card),
            )
            .clickable(onClick = onClick)
            .padding(RmSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Box(modifier = Modifier.size(RmSpacing.space3))
        // 선택 표식: 선택 시 primary 채운 점, 미선택 시 옅은 테두리 원(체크 아이콘 토큰이 없어 점으로 표현).
        Box(
            modifier = Modifier
                .size(RmSize.iconSm)
                .background(
                    if (selected) colors.primary else colors.transparent,
                    CircleShape,
                )
                .border(
                    RmSize.hairline,
                    if (selected) colors.primary else colors.border,
                    CircleShape,
                ),
        )
    }
}
