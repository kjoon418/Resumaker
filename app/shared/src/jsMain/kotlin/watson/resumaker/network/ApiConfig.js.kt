package watson.resumaker.network

/** config.js가 설정한 전역에서 API base URL을 읽는다(미설정이면 빈 문자열). */
private fun readApiBase(): String =
    js("(typeof window !== 'undefined' && window.__RESUMAKER_API_BASE__) ? window.__RESUMAKER_API_BASE__ : ''")

actual fun configuredApiBaseUrl(): String? = readApiBase().ifBlank { null }
