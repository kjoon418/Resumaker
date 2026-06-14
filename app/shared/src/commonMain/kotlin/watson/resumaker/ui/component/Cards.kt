package watson.resumaker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.model.type.ExperienceType
import watson.resumaker.ui.theme.RmElevation
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pressScale
import watson.resumaker.ui.theme.style

/**
 * 디자인 시스템 §5.3 RmCard. white 표면, 16dp radius, slate-100 테두리, shadow-sm.
 * [onClick]이 있으면 눌림 스케일 적용(클릭 가능 카드).
 */
@Composable
fun RmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = RmTheme.colors
    val base = modifier
        .fillMaxWidth()
        .shadow(RmElevation.card, RoundedCornerShape(RmRadius.card))
        .background(colors.surface, RoundedCornerShape(RmRadius.card))
        .border(1.dp, colors.borderSubtle, RoundedCornerShape(RmRadius.card))
    val clickable = if (onClick != null) base.pressScale(onClick = onClick) else base
    Box(modifier = clickable.padding(RmSpacing.space4)) {
        content()
    }
}

/**
 * 디자인 시스템 §5.4 ExperienceIconChip — 경험유형 아이콘칩(48dp, 유형 색).
 */
@Composable
fun ExperienceIconChip(
    type: ExperienceType,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = RmSize.iconChip,
) {
    val s = type.style()
    Box(
        modifier = modifier
            .size(size)
            .background(s.bg, RoundedCornerShape(RmRadius.chip)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = s.icon,
            contentDescription = s.label,
            tint = s.fg,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * 디자인 시스템 §5.3 ListItemCard — 좌측 리딩(아이콘칩 등) + 제목·메타 + 우측 chevron.
 */
@Composable
fun ListItemCard(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val colors = RmTheme.colors
    RmCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Box(modifier = Modifier.size(RmSpacing.space3))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = RmTextStyles.bodyM,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Box(modifier = Modifier.padding(top = RmSpacing.space1)) { badge() }
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = RmTextStyles.caption,
                        color = colors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = RmSpacing.space1),
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = RmIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(RmSize.iconMd),
                )
            }
        }
    }
}

/**
 * 디자인 시스템 §5.3 InfoCard — blue-50 안내/팁 박스. 좌측 아이콘 + 텍스트(또는 [content]).
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector = RmIcons.Info,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = RmTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primaryContainer, RoundedCornerShape(RmRadius.card))
            .border(BorderStroke(1.dp, colors.primaryBorder), RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space4),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(RmSize.iconMd),
            )
            Column(modifier = Modifier.padding(start = RmSpacing.space3)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = RmTextStyles.bodyS,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = RmSpacing.space1),
                    )
                }
                content()
            }
        }
    }
}

@Preview
@Composable
private fun CardsPreview() {
    RmTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItemCard(
                title = "결제 시스템 리팩터링",
                meta = "2024-01-01 ~ 2024-03-01",
                leading = { ExperienceIconChip(ExperienceType.PROJECT) },
                onClick = {},
                badge = { TypeBadge(ExperienceType.PROJECT) },
            )
            InfoCard(icon = RmIcons.Lightbulb, title = "이렇게 떠올려보세요") {
                Text(
                    text = "• 내가 직접 한 일은?",
                    style = RmTextStyles.bodyS,
                    color = RmTheme.colors.onPrimaryContainer,
                )
            }
        }
    }
}
