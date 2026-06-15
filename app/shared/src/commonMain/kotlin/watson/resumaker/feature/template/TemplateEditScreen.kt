package watson.resumaker.feature.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.type.SectionCharacter
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.InlineAddButton
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.SegmentedToggle
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 이력서 양식 생성·수정(FU-A). 양식 이름(필수) + 섹션 행 목록 편집(추가·삭제·순서 이동,
 * 각 행: 이름 / 성격 선택(요약형·경력형) / 필수 토글). TargetEditScreen 패턴을 미러한다.
 *
 * IA 검토 필요: 섹션 행 인라인 편집(폼빌더 축소판)의 정보구조·밀도는 최종 디자이너 리뷰 대상.
 */
@Composable
fun TemplateEditScreen(
    viewModel: TemplateEditViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = if (state.isEditMode) "양식 수정" else "양식 만들기",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> LoadingState(contentModifier, caption = "불러오는 중이에요")
            state.loadError != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(message = state.loadError!!, onRetry = viewModel::retryLoad, title = "불러오지 못했어요")
            }
            else -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space5),
            ) {
                RmTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = "양식 이름 *",
                    placeholder = "예: 토스 백엔드 지원용",
                    error = state.nameError,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "섹션 *",
                        style = RmTextStyles.titleL,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    InlineAddButton(text = "섹션 추가", onClick = viewModel::addSection)
                }
                Text(
                    text = "이력서에 담을 칸을 순서대로 정해요. 각 칸의 성격(요약형·경력형)과 필수 여부를 고를 수 있어요.",
                    style = RmTextStyles.caption,
                    color = colors.textTertiary,
                )
                if (state.sectionsError != null) {
                    Text(text = state.sectionsError!!, style = RmTextStyles.caption, color = colors.danger)
                }

                state.sections.forEachIndexed { index, row ->
                    SectionRowEditor(
                        row = row,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == state.sections.lastIndex,
                        canRemove = state.sections.size > 1,
                        onNameChange = { viewModel.onSectionNameChange(row.key, it) },
                        onCharacterChange = { viewModel.onSectionCharacterChange(row.key, it) },
                        onRequiredChange = { viewModel.onSectionRequiredChange(row.key, it) },
                        onMoveUp = { viewModel.moveSectionUp(row.key) },
                        onMoveDown = { viewModel.moveSectionDown(row.key) },
                        onRemove = { viewModel.removeSection(row.key) },
                    )
                }

                PrimaryButton(text = "저장", onClick = viewModel::save, loading = state.submitting)
            }
        }
    }
}

/** 단일 섹션 행 편집 카드: 순서/삭제 컨트롤 + 이름 + 성격 선택 + 필수 토글. */
@Composable
private fun SectionRowEditor(
    row: SectionRow,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onCharacterChange: (SectionCharacter) -> Unit,
    onRequiredChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = RmTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RmRadius.card))
            .border(RmSize.hairline, colors.borderSubtle, RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "섹션 ${index + 1}",
                style = RmTextStyles.label,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            // 순서 이동(위/아래). ChevronLeft/Right를 위/아래 의미로 재사용(전용 아이콘 없음).
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(
                    imageVector = RmIcons.ChevronLeft,
                    contentDescription = "위로 이동",
                    tint = if (isFirst) colors.textTertiary.copy(alpha = 0.4f) else colors.textSecondary,
                    modifier = Modifier.size(RmSize.iconSm),
                )
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(
                    imageVector = RmIcons.ChevronRight,
                    contentDescription = "아래로 이동",
                    tint = if (isLast) colors.textTertiary.copy(alpha = 0.4f) else colors.textSecondary,
                    modifier = Modifier.size(RmSize.iconSm),
                )
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(
                    imageVector = RmIcons.Close,
                    contentDescription = "섹션 삭제",
                    tint = if (canRemove) colors.textTertiary else colors.textTertiary.copy(alpha = 0.4f),
                    modifier = Modifier.size(RmSize.iconSm),
                )
            }
        }

        RmTextField(
            value = row.name,
            onValueChange = onNameChange,
            label = "섹션 이름 *",
            placeholder = "예: 핵심 역량",
            error = row.nameError,
        )

        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
            Text(text = "성격", style = RmTextStyles.label, color = colors.textLabel)
            SegmentedToggle(
                options = SectionCharacter.entries.map { it.label },
                selectedIndex = SectionCharacter.entries.indexOf(row.character),
                onSelect = { onCharacterChange(SectionCharacter.entries[it]) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "필수 섹션", style = RmTextStyles.label, color = colors.textLabel)
                Text(
                    text = "회사가 반드시 요구하는 칸이면 켜 주세요.",
                    style = RmTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
            Switch(
                checked = row.required,
                onCheckedChange = onRequiredChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                ),
            )
        }
    }
}
