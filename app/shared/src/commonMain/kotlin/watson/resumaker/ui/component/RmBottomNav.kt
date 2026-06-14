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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.7 하단 내비 탭(백엔드 범위 — 면접/AI생성 제외): 홈/경험/목표/마이.
 */
enum class RmTab(val label: String, val icon: ImageVector) {
    HOME("홈", RmIcons.Home),
    EXPERIENCE("경험", RmIcons.Note),
    TARGET("목표", RmIcons.Target),
    MY("마이", RmIcons.Person),
}

/**
 * 디자인 시스템 §5.7 RmBottomNav. 80dp, surface, top border slate-100.
 * 활성 primary+Bold, 비활성 textTertiary+Medium.
 */
@Composable
fun RmBottomNav(
    selected: RmTab,
    onSelect: (RmTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RmSize.bottomNavHeight)
                .background(colors.surface)
                .padding(top = RmSpacing.space1),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RmTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) colors.primary else colors.textTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = RmSpacing.space2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RmSpacing.space1),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(RmSize.iconNav),
                    )
                    Text(
                        text = tab.label,
                        style = RmTextStyles.overline.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = tint,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun RmBottomNavPreview() {
    RmTheme {
        RmBottomNav(selected = RmTab.HOME, onSelect = {})
    }
}
