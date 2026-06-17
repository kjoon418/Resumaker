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
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ComingSoon
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.SegmentedToggle
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 산출물 생성 진입 화면. 종류 선택 → 경험 다중 선택(필수) → 목표 선택(필수) → (이력서) 양식 선택(필수) → 생성.
 * 빈 경험묶음이면 ComingSoon 예방형 카피로 분기한다(수용 기준 8). 생성은 장시간(LLM)이라 버튼 로딩으로 고지.
 */
@Composable
fun ArtifactCreateScreen(
    viewModel: ArtifactCreateViewModel,
    onBack: () -> Unit,
    onGenerated: (watson.resumaker.model.dto.GenerationResponse) -> Unit,
    onRecordExperience: () -> Unit,
    onAddTarget: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.generated) {
        state.generated?.let { response ->
            viewModel.consumeGenerated()
            onGenerated(response)
        }
    }

    AppScaffold(
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = "이력서·포트폴리오 만들기",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
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
                onGenerate = viewModel::generate,
                onDismissError = viewModel::dismissGenerationError,
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
    onGenerate: () -> Unit,
    onDismissError: () -> Unit,
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
            EmptyHint("아직 목표가 없어요. 먼저 목표를 추가해 주세요.")
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

        if (state.templateRequired) {
            SectionLabel("어떤 양식을 쓸까요?")
            if (state.templates.isEmpty()) {
                EmptyHint("아직 양식이 없어요. 먼저 이력서 양식을 만들어 주세요.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
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
            ErrorBanner(message = state.generationError, onRetry = onDismissError, title = "생성하지 못했어요")
        }

        PrimaryButton(
            text = if (state.generating) "만드는 중…" else "만들기",
            onClick = onGenerate,
            enabled = state.canSubmit,
            loading = state.generating,
        )
        if (state.generating) {
            Text(
                text = "AI가 경험을 바탕으로 초안을 작성하고 있어요. 수십 초 걸릴 수 있어요.",
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
            Text(
                text = target.recruitDirection,
                style = RmTextStyles.bodyM,
                color = RmTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(target.companyName, target.jobTitle).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = RmTextStyles.caption,
                    color = RmTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = RmSpacing.space1),
                )
            }
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
