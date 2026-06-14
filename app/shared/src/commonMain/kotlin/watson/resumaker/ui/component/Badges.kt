package watson.resumaker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.model.type.ExperienceType
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.style

/**
 * 디자인 시스템 §5.5 Badge — 알약형 상태/유형 라벨. (전경, 옅은배경) 쌍으로 색을 받는다.
 */
@Composable
fun Badge(
    text: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(RmRadius.full))
            .padding(horizontal = RmSpacing.space2, vertical = RmSpacing.space0_5),
    ) {
        Text(text = text, style = RmTextStyles.captionBold, color = fg)
    }
}

/** 경험유형 배지(§6 색 쌍). */
@Composable
fun TypeBadge(type: ExperienceType, modifier: Modifier = Modifier) {
    val s = type.style()
    Badge(text = s.label, fg = s.fg, bg = s.bg, modifier = modifier)
}

/** "준비 중" 등 상태 배지(warning 쌍 기본). */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    fg: Color = RmTheme.colors.warning,
    bg: Color = RmTheme.colors.warningBg,
) {
    Badge(text = text, fg = fg, bg = bg, modifier = modifier)
}

/**
 * 디자인 시스템 §5.5 SkillTag — `#태그` 알약. [onRemove]가 있으면 우측 x.
 */
@Composable
fun SkillTag(
    text: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val colors = RmTheme.colors
    Row(
        modifier = modifier
            .background(colors.primaryContainer, RoundedCornerShape(RmRadius.full))
            .padding(horizontal = RmSpacing.space3, vertical = RmSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (text.startsWith("#")) text else "#$text",
            style = RmTextStyles.caption,
            color = colors.onPrimaryContainer,
        )
        if (onRemove != null) {
            Icon(
                imageVector = RmIcons.Close,
                contentDescription = "태그 삭제",
                tint = colors.onPrimaryContainer,
                modifier = Modifier
                    .padding(start = RmSpacing.space1)
                    .size(RmSize.iconXs)
                    .clickable { onRemove() },
            )
        }
    }
}

/**
 * 디자인 시스템 §5.5 AddChip — 점선 테두리 "+ 추가".
 */
@Composable
fun AddChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    Row(
        modifier = modifier
            .border(
                BorderStroke(RmSize.hairline, colors.border),
                RoundedCornerShape(RmRadius.full),
            )
            .clickable { onClick() }
            .padding(horizontal = RmSpacing.space3, vertical = RmSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space1),
    ) {
        Icon(
            imageVector = RmIcons.Add,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(RmSize.iconXs),
        )
        Text(text = text, style = RmTextStyles.caption, color = colors.textTertiary)
    }
}

@Preview
@Composable
private fun BadgesPreview() {
    RmTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TypeBadge(ExperienceType.AWARD)
            StatusBadge("준비 중")
            SkillTag("데이터분석", onRemove = {})
            AddChip("추가", onClick = {})
        }
    }
}
