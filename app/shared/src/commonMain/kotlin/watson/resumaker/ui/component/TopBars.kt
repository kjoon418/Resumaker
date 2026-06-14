package watson.resumaker.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.6 RmTopBar. 56dp, surface 배경, 중앙 타이틀.
 * [onBack]이 있으면 좌측 back, [actionText]가 있으면 우측 텍스트 액션(예 "저장").
 */
@Composable
fun RmTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    val colors = RmTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.topBarHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = RmTextStyles.headingS,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = RmIcons.ChevronLeft,
                    contentDescription = "뒤로",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(RmSize.iconMd),
                )
            }
        }
        if (actionText != null && onAction != null) {
            androidx.compose.material3.TextButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.align(Alignment.CenterEnd),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = colors.primary,
                    disabledContentColor = colors.primary.copy(alpha = 0.4f),
                ),
            ) {
                Text(text = actionText, style = RmTextStyles.bodyS.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

/**
 * 디자인 시스템 §5.6 BrandTopBar — 중앙 로고 "Resumaker"(진입/세션 화면).
 * 우측 [actionContent]로 마이 아이콘 등을 둘 수 있다(홈).
 */
@Composable
fun BrandTopBar(
    modifier: Modifier = Modifier,
    actionContent: (@Composable () -> Unit)? = null,
) {
    val colors = RmTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.topBarHeight)
            .padding(horizontal = RmSpacing.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Resumaker",
            style = RmTextStyles.headingS.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
        )
        if (actionContent != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) { actionContent() }
        }
    }
}

@Preview
@Composable
private fun TopBarsPreview() {
    RmTheme {
        androidx.compose.foundation.layout.Column {
            BrandTopBar(actionContent = {
                IconButton(onClick = {}) {
                    Icon(RmIcons.Person, contentDescription = "마이", tint = RmTheme.colors.textSecondary)
                }
            })
            RmTopBar(title = "경험 기록", onBack = {}, actionText = "저장", onAction = {})
        }
    }
}
