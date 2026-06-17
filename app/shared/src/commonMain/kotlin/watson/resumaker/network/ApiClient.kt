package watson.resumaker.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import watson.resumaker.model.dto.ErrorResponse
import watson.resumaker.session.SessionStore

/**
 * Ktor HttpClient 래퍼. ContentNegotiation(json) + 기본 URL + `X-User-Id` 헤더 주입을 담당한다(브리프 §API).
 *
 * @param baseUrl 백엔드 기본 URL(로컬 기본 `http://localhost:8082`, 환경설정 가능).
 * @param session 보호된 요청에 주입할 userId 출처.
 */
class ApiClient(
    val baseUrl: String = DEFAULT_BASE_URL,
    private val session: SessionStore,
    engineHttpClient: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val http: HttpClient = engineHttpClient.config {
        install(ContentNegotiation) {
            json(json)
        }
        // 산출물 생성은 LLM 호출이라 수십 초가 걸릴 수 있다. 일반 요청은 짧게, 생성 요청만 호출부에서
        // withLongTimeout()으로 길게 늘린다(JS 엔진은 requestTimeoutMillis만 적용된다).
        install(HttpTimeout) {
            requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MS
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
    }

    /** 보호된 요청에 현재 userId를 `X-User-Id`로 붙인다. userId가 없으면 붙이지 않는다. */
    fun HttpRequestBuilder.withUser() {
        session.currentUserId()?.let { header(HEADER_USER_ID, it) }
    }

    /** 산출물 생성처럼 장시간(LLM) 호출에 넉넉한 요청 타임아웃을 적용한다. */
    fun HttpRequestBuilder.withLongTimeout() {
        timeout { requestTimeoutMillis = GENERATION_REQUEST_TIMEOUT_MS }
    }

    fun url(path: String): String = baseUrl.trimEnd('/') + path

    /**
     * 요청을 실행하고 결과를 [ApiResult]로 변환한다.
     * 2xx면 [decode]로 본문을 파싱해 Success, 그 외에는 ErrorResponse를 파싱해 Failure로 만든다.
     * 네트워크/직렬화 예외는 기본 안내 Failure로 회수한다(막다른 길 금지).
     */
    suspend fun <T> safeRequest(
        decode: suspend (HttpResponse) -> T,
        request: suspend () -> HttpResponse,
    ): ApiResult<T> {
        return try {
            val response = request()
            if (response.status.isSuccess()) {
                ApiResult.Success(decode(response))
            } else {
                response.toFailureResult()
            }
        } catch (e: ResponseException) {
            e.response.toFailureResult()
        } catch (e: Throwable) {
            ApiResult.Failure(message = DEFAULT_NETWORK_ERROR)
        }
    }

    private suspend fun HttpResponse.toFailureResult(): ApiResult.Failure =
        try {
            body<ErrorResponse>().toFailure()
        } catch (e: Throwable) {
            ApiResult.Failure(message = DEFAULT_NETWORK_ERROR, code = status.value.toString())
        }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:8082"
        const val HEADER_USER_ID = "X-User-Id"

        /** 일반 요청 타임아웃(30초). */
        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L

        /** 산출물 생성(LLM) 요청 타임아웃(서버 CLI 타임아웃 120s보다 넉넉히, 180초). */
        const val GENERATION_REQUEST_TIMEOUT_MS = 180_000L
    }
}
