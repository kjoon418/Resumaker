package watson.resumaker.network

import io.ktor.client.HttpClient

/**
 * 플랫폼별 Ktor 엔진으로 기본 HttpClient를 생성한다(웹: Js 엔진).
 * commonMain은 엔진에 의존하지 않도록 expect로 분리한다(KMP 구조).
 */
expect fun createPlatformHttpClient(): HttpClient
