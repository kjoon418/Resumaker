@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package watson.resumaker.platform

/** navigator.clipboard.writeText 호출(실패해도 앱이 죽지 않게 무시). */
private fun writeTextToClipboard(text: String): Unit =
    js("{ if (navigator && navigator.clipboard) { navigator.clipboard.writeText(text); } }")

actual fun copyToClipboard(text: String) {
    try {
        writeTextToClipboard(text)
    } catch (e: Throwable) {
        // 클립보드 미지원/거부 시 무시(복사 실패는 치명적이지 않음).
    }
}
