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
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            // CSRF 방어: 모든 요청에 커스텀 헤더를 실어 보낸다. 서버 CsrfFilter는 상태 변경 요청에 이 헤더를 요구하며,
            // 브라우저는 교차 오리진 요청에 커스텀 헤더를 붙이려면 CORS 프리플라이트 승인을 받아야 하므로(허용 오리진만
            // 통과) 악성 사이트의 위조 요청을 막는다(쿠키 자동 첨부와 무관하게).
            header(REQUESTED_WITH_HEADER, REQUESTED_WITH_VALUE)
        }
        expectSuccess = false
    }

    /** 동시 401들이 동시에 refresh를 호출해 토큰을 과도하게 회전시키지 않도록 직렬화한다. */
    private val refreshMutex = Mutex()

    /**
     * 세션 만료(비자발적) 신호. access 401 후 refresh도 실패해 세션을 비운 경우 1회 방출한다.
     * 상위(App)가 관찰해 로그인 화면으로 리다이렉트한다. 자발적 로그아웃·탈퇴는 해당 화면이 직접 내비하므로
     * 여기서 방출하지 않는다. replay=0 + 버퍼 1로 수집자가 없어도 emit이 막히지 않게 한다(tryEmit).
     */
    private val _sessionExpirations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpirations: SharedFlow<Unit> = _sessionExpirations.asSharedFlow()

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
        authRetry: Boolean = true,
        request: suspend () -> HttpResponse,
    ): ApiResult<T> {
        return try {
            var response = request()
            // access 토큰 만료(401) → refresh 쿠키로 1회 재발급 후 원요청을 한 번만 재시도한다.
            // 인증 엔드포인트(로그인/가입/로그아웃)는 authRetry=false로 이 경로를 타지 않는다(무한 루프·무의미 방지).
            if (response.status == HttpStatusCode.Unauthorized && authRetry && refreshSession()) {
                response = request()
            }
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

    /**
     * access 만료 시 refresh 쿠키로 세션을 재발급한다(POST /auth/refresh, 204면 성공). 동시 401을 [refreshMutex]로
     * 직렬화한다. 실패하면(refresh도 만료/무효) 로컬 로그인 표시를 비워 다음 진입에서 로그인 화면으로 가게 한다.
     */
    private suspend fun refreshSession(): Boolean = refreshMutex.withLock {
        val refreshed = try {
            http.post(url("/auth/refresh")).status.isSuccess()
        } catch (e: Throwable) {
            false
        }
        if (!refreshed) {
            session.clear()
            // 비자발적 만료: 상위가 로그인 화면으로 보낼 수 있게 신호한다.
            _sessionExpirations.tryEmit(Unit)
        }
        refreshed
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

        /** CSRF 방어용 커스텀 헤더(서버 CsrfFilter가 상태 변경 요청에 요구). 값은 관례값이며 존재 자체가 핵심이다. */
        const val REQUESTED_WITH_HEADER = "X-Requested-With"
        const val REQUESTED_WITH_VALUE = "XMLHttpRequest"

        /** 일반 요청 타임아웃(30초). */
        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L

        /** 산출물 생성(LLM) 요청 타임아웃(서버 CLI 타임아웃 120s보다 넉넉히, 180초). */
        const val GENERATION_REQUEST_TIMEOUT_MS = 180_000L
    }
}
