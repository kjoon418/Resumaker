package watson.resumaker.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.BrandTopBar
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.SegmentedToggle
import watson.resumaker.ui.component.TextLink
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding
import watson.resumaker.validation.Validators

/**
 * 디자인 시스템 §8.1 세션 진입(가입 / 로그인).
 * 가입·로그인 모두 성공 시 곧장 홈으로 진입한다([onAuthenticated]).
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onAuthenticated: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.authenticatedUserId) {
        if (state.authenticatedUserId != null) onAuthenticated()
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.NARROW,
        header = { BrandTopBar() },
    ) { contentModifier, windowSize ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = windowSize.pagePadding()),
            verticalArrangement = Arrangement.spacedBy(RmSpacing.space5),
        ) {
            Spacer(Modifier.height(RmSpacing.space4))
            Text(text = "반가워요!", style = RmTextStyles.displayL, color = colors.textPrimary)
            Text(
                text = "경험을 기록하고, 목표에 맞춰 정리할 준비가 됐나요? AI와 함께 당신의 커리어를 채워가요.",
                style = RmTextStyles.bodyL,
                color = colors.textSecondary,
            )

            SegmentedToggle(
                options = listOf("가입하기", "로그인"),
                selectedIndex = if (state.mode == SessionMode.SIGN_UP) 0 else 1,
                onSelect = { viewModel.selectMode(if (it == 0) SessionMode.SIGN_UP else SessionMode.LOGIN) },
            )

            RmTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "이메일",
                placeholder = "name@example.com",
                keyboardType = KeyboardType.Email,
                error = state.emailError,
                imeAction = ImeAction.Next,
                onImeAction = { passwordFocusRequester.requestFocus() },
            )
            RmTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "비밀번호",
                placeholder = if (state.mode == SessionMode.SIGN_UP) "8자 이상 입력" else "비밀번호를 입력",
                isPassword = true,
                error = state.passwordError,
                helper = if (state.mode == SessionMode.SIGN_UP) {
                    Validators.passwordHelper(state.password, state.passwordError != null)
                } else {
                    null
                },
                imeAction = ImeAction.Done,
                onImeAction = viewModel::submit,
                focusRequester = passwordFocusRequester,
            )

            when (state.mode) {
                SessionMode.SIGN_UP -> {
                    Text(
                        text = "※ AI 생성 결과물의 최종 책임은 사용자에게 있어요. 사실 확인 후 사용해 주세요.",
                        style = RmTextStyles.caption,
                        color = colors.textTertiary,
                    )
                    PrimaryButton(
                        text = "가입하고 시작하기",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                    )
                    TextLink(
                        text = "이미 계정이 있으신가요? 로그인",
                        onClick = { viewModel.selectMode(SessionMode.LOGIN) },
                    )
                }

                SessionMode.LOGIN -> {
                    // 로그인 실패는 사라지는 토스트가 아니라 폼에 머무는 인라인 메시지로 보여준다(UX-09).
                    state.formError?.let { error ->
                        Text(
                            text = error,
                            style = RmTextStyles.bodyS,
                            color = colors.danger,
                        )
                    }
                    PrimaryButton(
                        text = "로그인",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                    )
                    TextLink(
                        text = "처음이신가요? 가입하기",
                        onClick = { viewModel.selectMode(SessionMode.SIGN_UP) },
                    )
                }
            }
            Spacer(Modifier.height(RmSpacing.space8))
        }
    }
}
