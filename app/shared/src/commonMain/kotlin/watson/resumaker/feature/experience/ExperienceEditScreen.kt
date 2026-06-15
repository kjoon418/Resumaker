package watson.resumaker.feature.experience

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AddChip
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ExperienceTypeSelector
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.SkillTag
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 디자인 시스템 §8.4 경험 생성·수정. 필수(제목·유형·본문) + 정적 회상 보조(플레이스홀더 + 유도질문) +
 * 선택 항목(STAR·기간·역량) 접기/펼치기.
 */
@Composable
fun ExperienceEditScreen(
    viewModel: ExperienceEditViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors
    val bodyFocusRequester = remember { FocusRequester() }

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
                title = if (state.isEditMode) "경험 수정" else "경험 기록",
                contentMaxWidth = ContentWidth.NARROW.maxWidth,
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
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = "제목 *",
                    placeholder = "예: 결제 시스템 응답 지연 개선",
                    error = state.titleError,
                    imeAction = ImeAction.Next,
                    onImeAction = { bodyFocusRequester.requestFocus() },
                )

                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                    Text(text = "유형 *", style = RmTextStyles.label, color = colors.textLabel)
                    ExperienceTypeSelector(selected = state.type, onSelect = viewModel::onTypeChange)
                    if (state.typeError != null) {
                        Text(text = state.typeError!!, style = RmTextStyles.caption, color = colors.danger)
                    }
                }

                RmTextField(
                    value = state.body,
                    onValueChange = viewModel::onBodyChange,
                    label = "본문 (무엇을 했는가) *",
                    placeholder = ExperienceEditViewModel.BODY_PLACEHOLDER,
                    error = state.bodyError,
                    singleLine = false,
                    minHeight = RmSize.multilineMinHeight,
                    focusRequester = bodyFocusRequester,
                )

                // 정적 회상 보조: 유도 질문 세트(§8.4, 항상 표시).
                InfoCard(icon = RmIcons.Lightbulb, title = "이렇게 떠올려보세요") {
                    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space1)) {
                        ExperienceEditViewModel.GUIDING_QUESTIONS.forEach { q ->
                            Text(text = "• $q", style = RmTextStyles.bodyS, color = colors.onPrimaryContainer)
                        }
                    }
                }

                OptionalSection(state = state, viewModel = viewModel)

                // WX-6: 폼 하단 인라인 저장(데스크톱 floating CTA 폐기).
                PrimaryButton(text = "저장", onClick = viewModel::save, loading = state.submitting)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OptionalSection(
    state: ExperienceEditUiState,
    viewModel: ExperienceEditViewModel,
) {
    val colors = RmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space4)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleOptional() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "선택 항목 (상황·행동·결과 / 기간 / 역량)",
                style = RmTextStyles.bodyS,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (state.optionalExpanded) RmIcons.ChevronLeft else RmIcons.ChevronRight,
                contentDescription = if (state.optionalExpanded) "접기" else "펼치기",
                tint = colors.textTertiary,
                modifier = Modifier.size(RmSize.iconSm),
            )
        }

        if (state.optionalExpanded) {
            RmTextField(
                value = state.situation,
                onValueChange = viewModel::onSituationChange,
                label = "상황 (S)",
                placeholder = "어떤 상황·배경이었나요?",
                singleLine = false,
                minHeight = RmSize.multilineMinHeight,
            )
            RmTextField(
                value = state.action,
                onValueChange = viewModel::onActionChange,
                label = "행동 (A)",
                placeholder = "내가 한 행동은?",
                singleLine = false,
                minHeight = RmSize.multilineMinHeight,
            )
            RmTextField(
                value = state.result,
                onValueChange = viewModel::onResultChange,
                label = "결과 (R)",
                placeholder = "결과적으로 무엇이 달라졌나요?",
                singleLine = false,
                minHeight = RmSize.multilineMinHeight,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                Box(modifier = Modifier.weight(1f)) {
                    RmTextField(
                        value = state.periodStart,
                        onValueChange = viewModel::onPeriodStartChange,
                        label = "시작 (YYYY-MM-DD)",
                        placeholder = "2024-01-01",
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    RmTextField(
                        value = state.periodEnd,
                        onValueChange = viewModel::onPeriodEndChange,
                        label = "종료 (YYYY-MM-DD)",
                        placeholder = "2024-03-01",
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    )
                }
            }
            // UX-1/UX-11: DatePicker 미도입 대신 형식 안내(트레이드오프는 핸드오프 참조).
            Text(
                text = "날짜는 연-월-일 순서로 적어 주세요. 예: 2024-01-01",
                style = RmTextStyles.caption,
                color = colors.textTertiary,
            )

            Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                Text(text = "사용 역량·기술", style = RmTextStyles.label, color = colors.textLabel)
                if (state.skillTags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2), verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                        state.skillTags.forEach { tag ->
                            SkillTag(text = tag, onRemove = { viewModel.removeSkill(tag) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                    Box(modifier = Modifier.weight(1f)) {
                        // UX-10: 비어 있던 라벨 대신 접근성 라벨을 부여(시각적으로도 입력 의도 명확).
                        RmTextField(
                            value = state.skillInput,
                            onValueChange = viewModel::onSkillInputChange,
                            label = "역량·기술 추가",
                            placeholder = "예: 데이터분석",
                            // UX-8: Enter(Done)로 태그 추가.
                            imeAction = ImeAction.Done,
                            onImeAction = viewModel::addSkill,
                        )
                    }
                    AddChip(text = "추가", onClick = viewModel::addSkill)
                }
            }
        }
        Spacer(Modifier.height(RmSpacing.space2))
    }
}
