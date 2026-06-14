package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §7 반응형 컨테이너. 루트 = background 위에 앱 컬럼(기본 440dp)을 수평 중앙 배치.
 * < 480dp 뷰포트에서는 컬럼이 fillMaxWidth(중앙정렬 무의미).
 *
 * 구조: [topBar](고정) / 본문(스크롤은 호출자 책임) / [bottomBar](고정).
 * [floatingBottom]은 본문 위에 떠 있는 하단 고정 CTA(컬럼 폭 기준).
 * 스낵바는 [snackbarHostState]로 표시한다.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    columnBackground: Boolean = false,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingBottom: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val colors = RmTheme.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isCompact = maxWidth < RmSize.appColumnMax
        val columnModifier = if (isCompact) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.width(RmSize.appColumnWidth)
        }

        Column(
            modifier = columnModifier
                .fillMaxHeight()
                .then(if (columnBackground) Modifier.background(colors.surface) else Modifier),
        ) {
            if (topBar != null) {
                Box(modifier = Modifier.background(colors.surface)) { topBar() }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content(Modifier.fillMaxSize())
                if (floatingBottom != null) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        floatingBottom()
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = RmSize.snackbarBottomGap),
                )
            }
            if (bottomBar != null) {
                bottomBar()
            }
        }
    }
}
