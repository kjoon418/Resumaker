package watson.resumaker.generation.infrastructure

import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * HTTP JSON POST seam([ProcessRunner]와 동형). 직접 Anthropic API를 호출하는 경로([ClaudeCliClient]의 API 분기)가
 * 실제 네트워크 호출을 이 인터페이스 뒤로 격리한다. 그래야 단위 테스트가 **실제 HTTP를 치지 않고** fake 구현
 * (canned 응답)을 주입할 수 있다(비용 0·결정성, 구현 설계 §10).
 *
 * @param url     POST 대상 URL.
 * @param headers 요청 헤더(인증 키·버전 등). 값은 로깅하지 않는다(비밀 포함).
 * @param body    요청 본문(JSON 문자열).
 * @param timeout 이 시간을 초과하면 [HttpJsonTimeoutException]을 던진다.
 */
interface HttpJsonClient {
    fun postJson(url: String, headers: Map<String, String>, body: String, timeout: Duration): HttpJsonResponse
}

/** HTTP 응답(상태코드·본문). 비정상 상태코드의 해석은 호출자([ClaudeCliClient])가 방어적으로 판정한다. */
data class HttpJsonResponse(val statusCode: Int, val body: String)

/** 요청이 타임아웃 안에 끝나지 않은 경우. 호출자가 명확한 실패로 전파한다. */
class HttpJsonTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 요청을 보내지 못했거나 연결이 실패한 경우. 호출자가 명확한 실패로 전파한다. */
class HttpJsonExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * JDK 내장 [java.net.http.HttpClient] 기반 프로덕션 구현(추가 의존성 없음). e2-micro에서 claude CLI(Node) 프로세스를
 * 띄우지 않고 JVM 안에서 HTTP 한 번으로 끝내, 생성당 메모리 부담을 제거한다.
 */
@Component
class JdkHttpJsonClient : HttpJsonClient {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        // 연결(TCP/TLS 핸드셰이크) 타임아웃. 한 호출 전체 대기는 요청별 timeout이 맡으므로 여기선
        // 연결 수립만 짧게 고정한다(설정 필드로 뺄 만큼 운영 변동이 없어 명명 상수로 충분).
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .build()

    override fun postJson(url: String, headers: Map<String, String>, body: String, timeout: Duration): HttpJsonResponse {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        headers.forEach { (name, value) -> builder.header(name, value) }

        return try {
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            HttpJsonResponse(response.statusCode(), response.body())
        } catch (e: java.net.http.HttpTimeoutException) {
            throw HttpJsonTimeoutException("HTTP 요청이 제한 시간을 초과했어요.", e)
        } catch (e: IOException) {
            throw HttpJsonExecutionException("HTTP 요청을 보내지 못했어요.", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw HttpJsonExecutionException("HTTP 요청이 중단됐어요.", e)
        }
    }

    companion object {
        /** 연결 수립 타임아웃(초). 운영 변동이 없어 고정한다. */
        private const val CONNECT_TIMEOUT_SECONDS = 15L
    }
}
