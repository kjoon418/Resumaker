package watson.resumaker.ui.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.model.type.ExperienceType
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.experienceTypesInOrder
import watson.resumaker.ui.theme.style

/**
 * 경험유형 칩 토글 그룹(§8.4 [P][J][E][A][L]). 단일 선택.
 * 선택된 칩은 유형 액센트 배경+테두리, 미선택은 옅은 표면.
 */
@Composable
fun ExperienceTypeSelector(
    selected: ExperienceType?,
    onSelect: (ExperienceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
    ) {
        experienceTypesInOrder.forEach { type ->
            val s = type.style()
            val active = type == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(type) }
                    .background(
                        if (active) s.bg else colors.surfaceSubtle,
                        RoundedCornerShape(RmRadius.chip),
                    )
                    .padding(vertical = RmSpacing.space2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space1),
            ) {
                Icon(
                    imageVector = s.icon,
                    contentDescription = s.label,
                    tint = if (active) s.fg else colors.textTertiary,
                    modifier = Modifier.size(RmSize.iconNav),
                )
                Text(
                    text = s.label,
                    style = RmTextStyles.overline.copy(
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (active) s.fg else colors.textTertiary,
                )
            }
        }
    }
}

/**
 * 2분기 세그먼트 컨트롤(§8.1 가입 / 재진입). slate-100 트랙 위 흰 선택 알약.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.segmentHeight)
            .background(colors.borderSubtle, RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space1),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .background(
                        if (active) colors.surface else colors.transparent,
                        RoundedCornerShape(RmRadius.segment),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = RmTextStyles.bodyS.copy(
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (active) colors.primary else colors.textSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SelectorsPreview() {
    RmTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedToggle(options = listOf("가입하기", "재진입"), selectedIndex = 0, onSelect = {})
            ExperienceTypeSelector(selected = ExperienceType.PROJECT, onSelect = {})
        }
    }
}
