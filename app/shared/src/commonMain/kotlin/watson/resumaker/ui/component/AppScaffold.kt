package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.WindowSize
import watson.resumaker.ui.theme.windowSizeFor

/**
 * 콘텐츠 컨테이너 폭 종류(WX-1). [WIDE]=리스트/홈(1120dp), [NARROW]=폼·세션(640dp).
 */
enum class ContentWidth(val maxWidth: Dp) {
    WIDE(RmSize.contentMaxWide),
    NARROW(RmSize.contentMaxNarrow),
}

/**
 * 디자인 시스템 §7 웹 반응형 스캐폴드(WX-1/7/9). 전체폭 sticky 헤더 + 중앙 콘텐츠 컨테이너 구조.
 *
 * - [header]: 전체폭(100vw) 상단 영역(예: [AppHeader] 또는 폼용 back 헤더). surface 배경은 호출자/헤더가 책임.
 * - 본문: [contentWidth] max-width로 중앙 정렬. content 람다에 (Modifier, WindowSize)를 넘겨
 *   화면이 패딩·그리드 열수를 직접 결정하게 한다.
 * - [floatingBottom]: Compact(<600px) 폴백용 하단 고정 CTA(데스크톱에선 미사용 권장). 컨테이너 폭 기준 정렬.
 * - 스낵바는 [snackbarHostState]로 콘텐츠 컨테이너 하단에 표시.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    contentWidth: ContentWidth = ContentWidth.WIDE,
    header: (@Composable (WindowSize) -> Unit)? = null,
    floatingBottom: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier, WindowSize) -> Unit,
) {
    val colors = RmTheme.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val windowSize = windowSizeFor(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
            if (header != null) {
                header(windowSize)
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // 콘텐츠 컨테이너: max-width 중앙 정렬(Expanded는 제한, 그 이하는 가용폭).
                val containerModifier = Modifier
                    .widthIn(max = contentWidth.maxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                content(containerModifier, windowSize)

                if (floatingBottom != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = contentWidth.maxWidth)
                            .fillMaxWidth(),
                    ) {
                        floatingBottom()
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = contentWidth.maxWidth)
                        .padding(bottom = RmSize.snackbarBottomGap),
                )
            }
        }
    }
}
