package watson.resumaker.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import watson.resumaker.fake.FakeSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ApiClient의 401(세션 만료) 처리 검증. Ktor MockEngine으로 HTTP 응답을 시뮬레이션한다.
 * 핵심 계약: access 401 후 refresh가 실패하면 세션을 비우고 [ApiClient.sessionExpirations]를 1회 방출한다
 * (상위 App이 로그인 화면으로 리다이렉트). refresh가 성공하면 원요청을 1회 재시도하고 만료를 알리지 않는다.
 */
class ApiClientTest {

    private fun client(session: FakeSessionStore, handler: io.ktor.client.engine.mock.MockRequestHandler): ApiClient =
        ApiClient(
            session = session,
            engineHttpClient = HttpClient(MockEngine) { engine { addHandler(handler) } },
        )

    @Test
    fun `401 후 refresh도 실패하면 세션을 비우고 만료를 1회 방출한다`() = runTest {
        val session = FakeSessionStore(userId = "u-1")
        // 보호 요청도 401, refresh(/auth/refresh)도 401 → 재발급 실패.
        val apiClient = client(session) { respond("", HttpStatusCode.Unauthorized) }

        val emissions = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            apiClient.sessionExpirations.collect { emissions += it }
        }

        val result = apiClient.safeRequest(decode = { it.status.value }) {
            apiClient.http.get(apiClient.url("/experiences"))
        }
        advanceUntilIdle()

        assertTrue(result is ApiResult.Failure, "401 응답은 Failure여야 한다")
        assertTrue(session.cleared, "refresh 실패 시 세션을 비워야 한다")
        assertEquals(1, emissions.size, "세션 만료는 정확히 1회 방출돼야 한다")
    }

    @Test
    fun `401 후 refresh가 성공하면 원요청을 재시도하고 만료를 알리지 않는다`() = runTest {
        val session = FakeSessionStore(userId = "u-1")
        var protectedCalls = 0
        val apiClient = client(session) { request ->
            when (request.url.encodedPath) {
                "/auth/refresh" -> respond("", HttpStatusCode.NoContent) // 재발급 성공
                else -> {
                    protectedCalls++
                    if (protectedCalls == 1) {
                        respond("", HttpStatusCode.Unauthorized) // 첫 호출: access 만료
                    } else {
                        respond("", HttpStatusCode.OK) // 재시도: 성공
                    }
                }
            }
        }

        val emissions = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            apiClient.sessionExpirations.collect { emissions += it }
        }

        val result = apiClient.safeRequest(decode = { it.status.value }) {
            apiClient.http.get(apiClient.url("/experiences"))
        }
        advanceUntilIdle()

        assertTrue(result is ApiResult.Success, "재발급 후 재시도는 성공해야 한다")
        assertEquals(200, result.value)
        assertEquals(2, protectedCalls, "원요청은 1회 재시도돼야 한다(총 2회)")
        assertFalse(session.cleared, "재발급 성공 시 세션을 비우면 안 된다")
        assertEquals(0, emissions.size, "재발급 성공 시 만료를 방출하면 안 된다")
    }

    @Test
    fun `인증 엔드포인트(authRetry=false)는 401이어도 refresh·만료 방출을 하지 않는다`() = runTest {
        val session = FakeSessionStore(userId = "u-1")
        var refreshCalls = 0
        val apiClient = client(session) { request ->
            if (request.url.encodedPath == "/auth/refresh") refreshCalls++
            respond("", HttpStatusCode.Unauthorized)
        }

        val emissions = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            apiClient.sessionExpirations.collect { emissions += it }
        }

        val result = apiClient.safeRequest(decode = { it.status.value }, authRetry = false) {
            apiClient.http.get(apiClient.url("/auth/login"))
        }
        advanceUntilIdle()

        assertTrue(result is ApiResult.Failure)
        assertEquals(0, refreshCalls, "authRetry=false는 refresh를 호출하면 안 된다")
        assertFalse(session.cleared)
        assertEquals(0, emissions.size)
    }
}
