package watson.resumaker.feature.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.BottomActionBar
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.RmTopBar
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §8.6 목표 생성·수정. 회사명·직무명(선택) + 채용 방향(필수).
 */
@Composable
fun TargetEditScreen(
    viewModel: TargetEditViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors
    val jobFocusRequester = remember { FocusRequester() }
    val directionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = true,
        topBar = {
            RmTopBar(
                title = if (state.isEditMode) "목표 수정" else "목표 추가",
                onBack = onBack,
            )
        },
        floatingBottom = {
            BottomActionBar {
                PrimaryButton(text = "저장", onClick = viewModel::save, loading = state.submitting)
            }
        },
    ) { contentModifier ->
        when {
            state.loading -> LoadingState(contentModifier, caption = "불러오는 중이에요")
            state.loadError != null -> Box(contentModifier.padding(RmSpacing.contentPadding)) {
                ErrorBanner(message = state.loadError!!, onRetry = viewModel::retryLoad, title = "불러오지 못했어요")
            }
            else -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RmSpacing.contentPadding)
                    .padding(top = RmSpacing.space4, bottom = RmSpacing.space10 + RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space5),
            ) {
                RmTextField(
                    value = state.companyName,
                    onValueChange = viewModel::onCompanyChange,
                    label = "회사명 (선택)",
                    placeholder = "예: 토스",
                    imeAction = ImeAction.Next,
                    onImeAction = { jobFocusRequester.requestFocus() },
                )
                RmTextField(
                    value = state.jobTitle,
                    onValueChange = viewModel::onJobTitleChange,
                    label = "직무명 (선택)",
                    placeholder = "예: 백엔드 엔지니어",
                    imeAction = ImeAction.Next,
                    onImeAction = { directionFocusRequester.requestFocus() },
                    focusRequester = jobFocusRequester,
                )
                RmTextField(
                    value = state.recruitDirection,
                    onValueChange = viewModel::onRecruitDirectionChange,
                    label = "채용 방향 *",
                    placeholder = "이 회사가 원하는 인재상·요구 역량을 적어 주세요.",
                    error = state.recruitDirectionError,
                    singleLine = false,
                    minHeight = RmSize.targetBodyMinHeight,
                    focusRequester = directionFocusRequester,
                )
                Text(
                    text = "※ 채용공고를 그대로 붙여넣어도 됩니다.",
                    style = RmTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}
