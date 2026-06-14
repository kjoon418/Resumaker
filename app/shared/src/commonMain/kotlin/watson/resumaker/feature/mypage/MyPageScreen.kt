package watson.resumaker.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.RmBottomNav
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.RmTab
import watson.resumaker.ui.component.RmTopBar
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §8.8 마이(계정) 간소화: 이메일·userId 표시, 로그아웃, 회원 탈퇴.
 * 백엔드 범위 밖(아바타/학력/경력 등)은 제외.
 */
@Composable
fun MyPageScreen(
    viewModel: MyPageViewModel,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onTabSelect: (RmTab) -> Unit,
    onCopyUserId: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors

    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = false,
        topBar = { RmTopBar(title = "마이페이지", onBack = onBack) },
        bottomBar = { RmBottomNav(selected = RmTab.MY, onSelect = onTabSelect) },
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .padding(horizontal = RmSpacing.contentPadding)
                .padding(top = RmSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
        ) {
            Text(text = "계정 정보", style = RmTextStyles.headingM, color = colors.textPrimary)

            RmCard {
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                    InfoRow(label = "이메일", value = state.email ?: "—")
                    Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "userId", style = RmTextStyles.caption, color = colors.textTertiary)
                            Text(
                                text = state.userId ?: "—",
                                style = RmTextStyles.bodyS,
                                color = colors.textBody,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (state.userId != null) {
                            IconButton(onClick = { onCopyUserId(state.userId!!) }) {
                                Icon(
                                    imageVector = RmIcons.Copy,
                                    contentDescription = "userId 복사",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(RmSize.iconSm),
                                )
                            }
                        }
                    }
                }
            }

            // 재로그인 열쇠 안내(P0-2 보강): userId 보관 필요성을 마이페이지에서도 상시 고지.
            InfoCard(icon = RmIcons.Info, title = "userId는 재로그인 열쇠예요") {
                Text(
                    text = "로그아웃하면 이 기기에서 userId가 지워져요. 위 복사 버튼으로 안전한 곳에 보관해 두세요.",
                    style = RmTextStyles.bodyS,
                    color = colors.onPrimaryContainer,
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))

            AccountActionRow(text = "로그아웃", danger = false, onClick = viewModel::requestLogout)
            AccountActionRow(text = "회원 탈퇴", danger = true, onClick = viewModel::requestDelete)
        }
    }

    if (state.confirmingLogout) {
        ConfirmDialog(
            title = "로그아웃하시겠어요?",
            description = "로그아웃하면 이 기기에서 userId가 지워져요. 다시 들어오려면 userId가 필요해요. 복사해 두셨나요? 아직이면 취소하고 위 userId를 복사해 주세요.",
            confirmText = "로그아웃",
            onConfirm = viewModel::confirmLogout,
            onDismiss = viewModel::cancelLogout,
        )
    }

    if (state.confirmingDelete) {
        ConfirmDialog(
            title = "정말 탈퇴하시겠어요?",
            description = "모든 경험·목표가 함께 삭제되며, 되돌릴 수 없어요.",
            confirmText = "탈퇴",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = RmTheme.colors
    Column {
        Text(text = label, style = RmTextStyles.caption, color = colors.textTertiary)
        Text(text = value, style = RmTextStyles.bodyS, color = colors.textBody)
    }
}

@Composable
private fun AccountActionRow(text: String, danger: Boolean, onClick: () -> Unit) {
    val colors = RmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = RmSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = RmTextStyles.bodyS,
            color = if (danger) colors.danger else colors.textBody,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = RmIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(RmSize.iconSm),
        )
    }
}
