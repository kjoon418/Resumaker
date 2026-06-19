package watson.resumaker.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

/**
 * JS(레거시) 플랫폼 HttpClient. wasmJs와 동일하게 fetch `credentials: "include"`로 교차 출처 인증 쿠키를 전송한다.
 * JS 타깃의 `RequestInit.credentials`는 `dynamic`이라 문자열을 그대로 대입한다.
 */
actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js) {
    engine {
        configureRequest {
            credentials = "include"
        }
    }
}
