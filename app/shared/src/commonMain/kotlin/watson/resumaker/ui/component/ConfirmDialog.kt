package watson.resumaker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.12 ConfirmDialog — 파괴적 행동(삭제/탈퇴) 확인.
 * [description]에는 "무엇이 사라지는지"를 명시한다(도메인 §271 되돌릴 수 없음 고지).
 * 확인 버튼은 danger 강조.
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
) {
    val colors = RmTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(RmRadius.card),
        modifier = modifier,
        title = { Text(text = title, style = RmTextStyles.headingM, color = colors.textPrimary) },
        text = { Text(text = description, style = RmTextStyles.bodyS, color = colors.textSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(RmRadius.card),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(text = confirmText, style = RmTextStyles.label.copy(fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textLabel),
            ) {
                Text(text = cancelText, style = RmTextStyles.label)
            }
        },
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
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
