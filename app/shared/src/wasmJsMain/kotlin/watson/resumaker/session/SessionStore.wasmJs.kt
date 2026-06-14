package watson.resumaker.session

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * 웹(Wasm) localStorage 기반 세션 보관소. 브라우저 새로고침 후에도 재진입 가능하도록 영속한다.
 */
private class WebSessionStore : SessionStore {
    override fun currentUserId(): String? = localStorage[KEY_USER_ID]
    override fun currentEmail(): String? = localStorage[KEY_EMAIL]

    override fun save(userId: String, email: String?) {
        localStorage[KEY_USER_ID] = userId
        if (email != null) localStorage[KEY_EMAIL] = email
    }

    override fun clear() {
        localStorage.removeItem(KEY_USER_ID)
        localStorage.removeItem(KEY_EMAIL)
    }

    private companion object {
        const val KEY_USER_ID = "resumaker.userId"
        const val KEY_EMAIL = "resumaker.email"
    }
}

actual fun createSessionStore(): SessionStore = WebSessionStore()
