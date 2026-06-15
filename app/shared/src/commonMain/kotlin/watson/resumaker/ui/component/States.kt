package watson.resumaker.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmMotion
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
                .size(RmSize.emblem)
                .background(colors.surfaceSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(RmSize.iconLg),
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
        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(RmSize.iconLg))
        if (caption != null) {
            Text(text = caption, style = RmTextStyles.bodyS, color = colors.textSecondary)
        }
    }
}

/**
 * 디자인 시스템 §5.9 스켈레톤 — 리스트 로딩 시 레이아웃 점프 방지(원칙 7 체감성능).
 * slate-100(borderSubtle) 박스 + 은은한 shimmer(alpha 보간). 토큰만 사용.
 * 리스트 카드 1행(아이콘칩 + 제목 라인 + 메타 라인) 형태를 모사한다.
 *
 * @param showLeadingChip 좌측 아이콘칩 자리 표시 여부(경험 리스트=true, 목표 리스트=false).
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    showLeadingChip: Boolean = true,
) {
    val colors = RmTheme.colors
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(RmMotion.durBase * 4),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.skeletonItemHeight)
            .background(colors.surface, RoundedCornerShape(RmRadius.card))
            .border(RmSize.hairline, colors.borderSubtle, RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space4),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showLeadingChip) {
                SkeletonBlock(
                    width = RmSize.iconChip,
                    height = RmSize.iconChip,
                    radius = RmRadius.chip,
                    alpha = shimmerAlpha,
                )
                Box(modifier = Modifier.size(RmSpacing.space3))
            }
            Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                SkeletonBlock(
                    width = RmSize.skeletonTitleWidth,
                    height = RmSize.skeletonLineLg,
                    radius = RmRadius.sm,
                    alpha = shimmerAlpha,
                )
                SkeletonBlock(
                    width = RmSize.skeletonMetaWidth,
                    height = RmSize.skeletonLineSm,
                    radius = RmRadius.sm,
                    alpha = shimmerAlpha,
                )
            }
        }
    }
}

/** 스켈레톤 N행을 카드 간격(space3)으로 쌓는다. 리스트 로딩에 사용. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 3,
    showLeadingChip: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        repeat(count) {
            SkeletonListItem(showLeadingChip = showLeadingChip)
        }
    }
}

@Composable
private fun SkeletonBlock(width: Dp, height: Dp, radius: Dp, alpha: Float) {
    val colors = RmTheme.colors
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .alpha(alpha)
            .background(colors.borderSubtle, RoundedCornerShape(radius)),
    )
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
            // UX-13: 위험 상태 전달 아이콘은 Info가 아닌 Warning 계열. UX-6: 제목만으로 위험을
            // 충분히 전달하지 못하므로 스크린리더용 contentDescription을 부여한다.
            Icon(
                imageVector = RmIcons.Warning,
                contentDescription = "경고",
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
