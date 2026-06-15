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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 디자인 시스템 §8.8 마이(계정) 간소화: 이메일 표시, 로그아웃, 회원 탈퇴.
 * 백엔드 범위 밖(아바타/학력/경력 등)은 제외. 로그인이 도입되어 "복구 코드" 노출은 제거한다.
 */
@Composable
fun MyPageScreen(
    viewModel: MyPageViewModel,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onSelectTab: (HeaderTab) -> Unit,
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
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            // WX-7/14: 마이는 헤더 계정 메뉴에서 진입 — 헤더 탭 강조 없음(selected=null), 계정 아이콘은 현 화면.
            AppHeader(
                selected = null,
                onSelectTab = onSelectTab,
                onOpenAccount = onBack,
                windowSize = windowSize,
                contentMaxWidth = ContentWidth.NARROW.maxWidth,
                horizontalPadding = windowSize.pagePadding(),
            )
        },
    ) { contentModifier, windowSize ->
        Column(
            modifier = contentModifier
                .padding(horizontal = windowSize.pagePadding())
                .padding(top = RmSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
        ) {
            Text(text = "마이페이지", style = RmTextStyles.titleL, color = colors.textPrimary)
            Text(text = "계정 정보", style = RmTextStyles.headingM, color = colors.textPrimary)

            RmCard {
                InfoRow(label = "이메일", value = state.email ?: "—")
            }

            Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))

            AccountActionRow(text = "로그아웃", danger = false, onClick = viewModel::requestLogout)
            AccountActionRow(text = "회원 탈퇴", danger = true, onClick = viewModel::requestDelete)
        }
    }

    if (state.confirmingLogout) {
        ConfirmDialog(
            title = "로그아웃하시겠어요?",
            description = "로그아웃하면 이 기기에서 세션이 종료돼요. 다시 들어오려면 이메일과 비밀번호로 로그인하면 돼요.",
            confirmText = "로그아웃",
            onConfirm = viewModel::confirmLogout,
            onDismiss = viewModel::cancelLogout,
            // OBS-2: 로그아웃은 비파괴 행동 — danger 강조 불필요, primary 톤 사용.
            destructive = false,
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
