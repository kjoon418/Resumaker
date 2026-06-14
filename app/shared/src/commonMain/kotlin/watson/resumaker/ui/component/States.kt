package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.8 EmptyState — "백지의 공포 최소화"를 책임지는 1급 컴포넌트.
 * 아이콘(원형 옅은 배경) + 제목 + 설명 + Primary 액션. 카피는 다음 행동 제시(비난/공허 금지).
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = RmIcons.Inbox,
) {
    val colors = RmTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RmSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.surfaceSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(text = title, style = RmTextStyles.headingM, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            text = description,
            style = RmTextStyles.bodyS,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = actionText, onClick = onAction)
    }
}

/**
 * 디자인 시스템 §5.9 전체 로딩 — 중앙 인디케이터 + 선택 캡션.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = RmTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(RmSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(32.dp))
        if (caption != null) {
            Text(text = caption, style = RmTextStyles.bodyS, color = colors.textSecondary)
        }
    }
}

/**
 * 디자인 시스템 §5.10 ErrorBanner — 섹션/네트워크 에러. "다시 시도"로 막다른 길 방지.
 */
@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "문제가 생겼어요",
) {
    val colors = RmTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.dangerBg, RoundedCornerShape(RmRadius.card))
            .border(RmSize.hairline, colors.danger.copy(alpha = 0.3f), RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = RmIcons.Info,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(RmSize.iconMd),
            )
            Column(modifier = Modifier.padding(start = RmSpacing.space3)) {
                Text(text = title, style = RmTextStyles.bodyM, color = colors.textPrimary)
                Text(
                    text = message,
                    style = RmTextStyles.bodyS,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = RmSpacing.space1),
                )
            }
        }
        GhostButton(text = "다시 시도", onClick = onRetry)
    }
}

@Preview
@Composable
private fun StatesPreview() {
    RmTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyState(
                title = "아직 기록한 경험이 없어요",
                description = "첫 경험을 기록해 볼까요?",
                actionText = "경험 기록하기",
                onAction = {},
            )
            ErrorBanner(message = "잠시 후 다시 시도해 주세요.", onRetry = {})
        }
    }
}
