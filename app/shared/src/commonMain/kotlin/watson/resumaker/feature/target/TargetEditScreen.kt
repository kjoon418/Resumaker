package watson.resumaker.feature.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 디자인 시스템 §8.6 목표 생성·수정. 회사명·직무명(선택) + 채용 방향(필수).
 */
@Composable
fun TargetEditScreen(
    viewModel: TargetEditViewModel,
    onBack: () -> Unit,
    /** 저장 성공 시 1회 호출. 인자는 목록에서 노출할 성공 스낵바 문구(생성/수정·전략 유무로 분기). */
    onSaved: (message: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val jobFocusRequester = remember { FocusRequester() }
    val directionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.saved) { if (state.saved) onSaved(state.savedMessage) }
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
                title = if (state.isEditMode) "목표 수정" else "목표 추가",
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
                // 채용 방향 수정 시 전략 재분석 안내(편집 모드 + 기존 전략 READY일 때만).
                if (state.showStrategyReanalyzeInfo) {
                    InfoCard(title = "채용 방향을 수정하면 전략이 다시 분석돼요") {
                        Text(
                            text = "공고 내용을 업데이트하면 AI가 새 전략을 만들어 드려요.",
                            style = RmTextStyles.bodyS,
                            color = RmTheme.colors.onPrimaryContainer,
                        )
                    }
                }
                RmTextField(
                    value = state.recruitDirection,
                    onValueChange = viewModel::onRecruitDirectionChange,
                    label = "채용 방향 *",
                    placeholder = "이 회사가 원하는 인재상·요구 역량을 적어 주세요.",
                    helper = "공고를 통째로 붙여넣어도 괜찮아요. 저장하면 AI가 핵심 전략을 정리해 드려요. (${state.recruitDirection.length}/5000)",
                    error = state.recruitDirectionError,
                    singleLine = false,
                    minHeight = RmSize.targetBodyMinHeight,
                    focusRequester = directionFocusRequester,
                )

                // WX-6: 폼 하단 인라인 저장(데스크톱 floating CTA 폐기).
                PrimaryButton(text = "저장", onClick = viewModel::save, loading = state.submitting)
            }
        }
    }
}
