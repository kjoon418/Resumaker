package watson.resumaker.navigation

import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * 웹(JS) History API 구현(WX-8; wasmJs와 동일 동작, JS fallback 타깃용).
 */
actual class BrowserHistory actual constructor() {
    actual fun currentPath(): String {
        val path = window.location.pathname
        return if (path.isEmpty()) "/" else path
    }

    actual fun push(path: String) {
        window.history.pushState(null, "", path)
    }

    actual fun replace(path: String) {
        window.history.replaceState(null, "", path)
    }

    actual fun back() {
        window.history.back()
    }

    actual fun onPopState(listener: (String) -> Unit) {
        window.addEventListener("popstate", { _: Event ->
            val path = window.location.pathname
            listener(if (path.isEmpty()) "/" else path)
        })
    }
}
