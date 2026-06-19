package watson.resumaker.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

/**
 * wasmJs 플랫폼 HttpClient. 다른 도메인(cross-site) 백엔드로 인증 쿠키를 보내려면 fetch `credentials: "include"`가
 * 필요하다(브라우저 기본은 same-origin이라 교차 출처 쿠키를 보내지 않음). Ktor 3.x JS 엔진의 `configureRequest`로
 * 모든 요청의 RequestInit에 credentials를 설정한다. wasmJs에서 `credentials`는 `JsAny?`라 JsString으로 넣는다.
 */
actual fun createPlatformHttpClient(): HttpClient = HttpClient(Js) {
    engine {
        configureRequest {
            credentials = "include".toJsString()
        }
    }
}
