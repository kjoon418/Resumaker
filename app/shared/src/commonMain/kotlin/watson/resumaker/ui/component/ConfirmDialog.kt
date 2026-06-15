package watson.resumaker.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.12 ConfirmDialog — 행동 확인 다이얼로그.
 * [description]에는 "무엇이 사라지는지"를 명시한다(도메인 §271 되돌릴 수 없음 고지).
 *
 * [destructive] = true(기본): 확인 버튼을 danger 강조 — 삭제·탈퇴 등 파괴적 행동에 사용.
 * [destructive] = false: 확인 버튼을 primary 색상 — 로그아웃 등 비파괴 행동에 사용.
 * 취소 버튼은 §5.12 GhostButton(outline, radius16) 사용.
 */
@Composable
fun ConfirmDialog(
    title: String,
    description: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "취소",
    /** true(기본)면 확인 버튼을 danger로, false면 primary 톤으로 렌더. */
    destructive: Boolean = true,
) {
    val colors = RmTheme.colors
    val confirmContainerColor = if (destructive) colors.danger else colors.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(RmRadius.card),
        modifier = modifier,
        title = { Text(text = title, style = RmTextStyles.headingM, color = colors.textPrimary) },
        text = {
            Text(text = description, style = RmTextStyles.bodyS, color = colors.textSecondary)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(RmRadius.card),
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmContainerColor,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(text = confirmText, style = RmTextStyles.label.copy(fontWeight = FontWeight.Bold))
            }
        },
        // OBS-1: §5.12 취소 버튼을 GhostButton(outline, radius16)으로 교체.
        // fillWidth=false로 dialog Row 내 전체 폭 확장을 억제하고 내용 폭에 맞춤.
        dismissButton = {
            GhostButton(
                text = cancelText,
                onClick = onDismiss,
                fillWidth = false,
            )
        },
    )
}

@Preview
@Composable
private fun ConfirmDialogDestructivePreview() {
    RmTheme {
        ConfirmDialog(
            title = "정말 탈퇴하시겠어요?",
            description = "모든 경험·목표가 함께 삭제되며 되돌릴 수 없어요.",
            confirmText = "탈퇴",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ConfirmDialogNonDestructivePreview() {
    RmTheme {
        ConfirmDialog(
            title = "로그아웃하시겠어요?",
            description = "로그아웃하면 이 기기에서 세션이 종료돼요. 다시 들어오려면 이메일과 비밀번호로 로그인하면 돼요.",
            confirmText = "로그아웃",
            onConfirm = {},
            onDismiss = {},
            destructive = false,
        )
    }
}
