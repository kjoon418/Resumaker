package watson.resumaker.platform

import kotlinx.browser.window

actual fun copyToClipboard(text: String) {
    try {
        window.navigator.clipboard.writeText(text)
    } catch (e: Throwable) {
        // 클립보드 미지원/거부 시 무시.
    }
}
