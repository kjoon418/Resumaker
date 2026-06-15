package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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

/**
 * 디자인 시스템 §5.6/§7 PageHeader(WX-9). 전체폭 64dp 웹 헤더 — 폼·세션·상세 화면용.
 * 좌측 back chevron + 좌측 정렬 타이틀, 우측 옵션 [actionText]. 콘텐츠는 [LocalContentMaxWidth](본문 폭)로 중앙 제한.
 * 헤더 막대는 전체폭 surface + 하단 헤어라인.
 */
@Composable
fun PageHeader(
    title: String,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    val colors = RmTheme.colors
    val contentMaxWidth = LocalContentMaxWidth.current
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .height(RmSize.headerHeight)
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(end = RmSpacing.space1),
                    ) {
                        Icon(
                            imageVector = RmIcons.ChevronLeft,
                            contentDescription = "뒤로",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(RmSize.iconMd),
                        )
                    }
                }
                Text(
                    text = title,
                    style = RmTextStyles.headingS,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (actionText != null && onAction != null) {
                    androidx.compose.material3.TextButton(
                        onClick = onAction,
                        enabled = actionEnabled,
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
        Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))
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
