package watson.resumaker.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.BrandTopBar
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.SegmentedToggle
import watson.resumaker.ui.component.TextLink
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §8.1 세션 진입(가입 / userId 재진입).
 * 가입 성공 시 곧장 홈으로 가지 않고 userId 보관 고지 화면을 띄운다(P0-1). 확인 후 [onAuthenticated]로 진입.
 *
 * @param onCopyUserId userId를 클립보드에 복사(플랫폼 의존, App에서 주입).
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onAuthenticated: () -> Unit,
    onCopyUserId: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors

    LaunchedEffect(state.authenticatedUserId) {
        if (state.authenticatedUserId != null) onAuthenticated()
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    // 가입 직후 userId 보관 고지 화면(P0-1): 사용자가 확인하기 전까지 폼 대신 이 화면만 보여준다.
    val issuedUserId = state.issuedUserId
    if (issuedUserId != null && state.authenticatedUserId == null) {
        IssuedUserIdView(
            userId = issuedUserId,
            snackbarHostState = snackbarHostState,
            onCopy = {
                onCopyUserId(issuedUserId)
            },
            onAcknowledge = viewModel::acknowledgeUserId,
        )
        return
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = true,
        topBar = { BrandTopBar() },
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RmSpacing.contentPadding),
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
                options = listOf("가입하기", "재진입"),
                selectedIndex = if (state.mode == SessionMode.SIGN_UP) 0 else 1,
                onSelect = { viewModel.selectMode(if (it == 0) SessionMode.SIGN_UP else SessionMode.REENTER) },
            )

            when (state.mode) {
                SessionMode.SIGN_UP -> {
                    RmTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "이메일",
                        placeholder = "name@example.com",
                        keyboardType = KeyboardType.Email,
                        error = state.emailError,
                    )
                    RmTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = "비밀번호",
                        placeholder = "8자 이상 입력",
                        isPassword = true,
                        error = state.passwordError,
                        helper = if (state.passwordError == null) "8자 이상으로 입력해 주세요." else null,
                    )
                    Text(
                        text = "※ AI 생성 결과물의 최종 책임은 사용자에게 있어요. 사실 확인 후 사용해 주세요.",
                        style = RmTextStyles.caption,
                        color = colors.textTertiary,
                    )
                    PrimaryButton(
                        text = "회원가입 완료",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                    )
                    TextLink(
                        text = "이미 userId가 있으신가요? 재진입",
                        onClick = { viewModel.selectMode(SessionMode.REENTER) },
                    )
                }

                SessionMode.REENTER -> {
                    RmTextField(
                        value = state.userIdInput,
                        onValueChange = viewModel::onUserIdChange,
                        label = "userId",
                        placeholder = "가입 시 발급받은 userId(UUID)",
                        error = state.userIdError,
                        helper = if (state.userIdError == null) "마이페이지에서 복사한 userId를 붙여넣어 주세요." else null,
                    )
                    PrimaryButton(text = "재진입", onClick = viewModel::submit)
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

/**
 * P0-1: 가입 직후 발급된 userId를 화면에 노출하고 보관을 유도한다(도메인 §62·§275, 수용기준 14).
 * 로그인 API가 없어 userId가 재로그인 유일 열쇠이므로, 복사 + 경고 + 확인 후에만 진입시킨다.
 */
@Composable
private fun IssuedUserIdView(
    userId: String,
    snackbarHostState: SnackbarHostState,
    onCopy: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val colors = RmTheme.colors
    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = true,
        topBar = { BrandTopBar() },
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RmSpacing.contentPadding),
            verticalArrangement = Arrangement.spacedBy(RmSpacing.space5),
        ) {
            Spacer(Modifier.height(RmSpacing.space4))
            Text(text = "가입이 완료됐어요!", style = RmTextStyles.displayL, color = colors.textPrimary)
            Text(
                text = "아래 userId가 당신의 재로그인 열쇠예요. 이 서비스는 별도 로그인이 없어, 다른 기기나 브라우저에서 다시 들어오려면 이 값이 꼭 필요해요.",
                style = RmTextStyles.bodyL,
                color = colors.textSecondary,
            )

            Text(text = "내 userId", style = RmTextStyles.label, color = colors.textLabel)
            RmCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userId,
                        style = RmTextStyles.bodyS,
                        color = colors.textBody,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = RmIcons.Copy,
                            contentDescription = "userId 복사",
                            tint = colors.primary,
                            modifier = Modifier.size(RmSize.iconSm),
                        )
                    }
                }
            }

            InfoCard(icon = RmIcons.Info, title = "꼭 복사해 안전한 곳에 보관하세요") {
                Text(
                    text = "userId를 잃어버리면 기록한 경험·목표에 다시 접근할 수 없어요. 비밀번호 관리자나 메모에 저장해 두는 것을 권장해요.",
                    style = RmTextStyles.bodyS,
                    color = colors.onPrimaryContainer,
                )
            }

            PrimaryButton(text = "복사했어요, 시작하기", onClick = onAcknowledge)
            Spacer(Modifier.height(RmSpacing.space8))
        }
    }
}
