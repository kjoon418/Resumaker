package watson.resumaker.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 단순 스택 기반 내비게이터. 현재 화면과 뒤로가기 스택을 보관한다(단방향: 화면은 이벤트만 호출).
 *
 * 탭 전환은 스택을 리셋(루트 교체)하고, 상세 진입은 push, 뒤로가기는 pop한다.
 */
@Stable
class AppNavigator(start: Screen) {
    private val backStack = ArrayDeque<Screen>().apply { addLast(start) }

    var current by mutableStateOf(start)
        private set

    val canGoBack: Boolean get() = backStack.size > 1

    /** 상세/하위 화면으로 진입(스택 push). */
    fun push(screen: Screen) {
        backStack.addLast(screen)
        current = screen
    }

    /** 탭/루트 전환: 스택을 단일 루트로 리셋. */
    fun switchRoot(screen: Screen) {
        backStack.clear()
        backStack.addLast(screen)
        current = screen
    }

    /** 뒤로가기(스택 pop). 루트면 무시. */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLast()
            current = backStack.last()
        }
    }

    /** 인증 진입/만료 시 세션 화면으로 완전 리셋. */
    fun resetToSession() = switchRoot(Screen.Session)
}
