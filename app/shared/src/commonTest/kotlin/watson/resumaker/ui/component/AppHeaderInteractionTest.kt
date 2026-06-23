package watson.resumaker.ui.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.WindowSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AppHeader 클릭 상호작용 검증. Compose Wasm은 캔버스 렌더라 셀렉터 기반 브라우저 클릭이 어려우므로,
 * 실제 컴포저블을 렌더하고 클릭 제스처를 주입하는 UI 테스트로 로고/탭 내비를 검증한다.
 */
class AppHeaderInteractionTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun clickingLogoSelectsHomeTab() = runComposeUiTest {
        var selected: HeaderTab? = null
        setContent {
            RmTheme {
                AppHeader(
                    selected = HeaderTab.EXPERIENCE,
                    onSelectTab = { selected = it },
                    onOpenAccount = {},
                    windowSize = WindowSize.EXPANDED,
                    horizontalPadding = RmSpacing.space8,
                )
            }
        }

        onNodeWithContentDescription("홈으로").performClick()

        assertEquals(HeaderTab.HOME, selected)
    }

    /** 진단 기준선: 동일한 pressScale 제스처를 쓰는 일반 탭 클릭이 동작하는지 함께 확인한다. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun clickingArtifactTabSelectsArtifact() = runComposeUiTest {
        var selected: HeaderTab? = null
        setContent {
            RmTheme {
                AppHeader(
                    selected = HeaderTab.HOME,
                    onSelectTab = { selected = it },
                    onOpenAccount = {},
                    windowSize = WindowSize.EXPANDED,
                    horizontalPadding = RmSpacing.space8,
                )
            }
        }

        onNodeWithText("산출물").performClick()

        assertEquals(HeaderTab.ARTIFACT, selected)
    }
}
