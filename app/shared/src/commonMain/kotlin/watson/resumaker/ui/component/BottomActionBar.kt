package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTheme

/**
 * 하단 고정 CTA 컨테이너(§4 레퍼런스 하단 고정 액션 패턴). surface 배경 + 상단 보더로 본문과 분리한다.
 * [AppScaffold.floatingBottom] 슬롯에 넣어 컬럼 폭 기준으로 정렬한다.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = RmTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = RmSpacing.contentPadding, vertical = RmSpacing.space3),
        ) {
            content()
        }
    }
}
