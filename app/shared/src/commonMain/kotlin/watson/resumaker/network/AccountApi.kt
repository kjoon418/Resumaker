package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.LoginRequest
import watson.resumaker.model.dto.LoginResponse
import watson.resumaker.model.dto.SignUpRequest
import watson.resumaker.model.dto.SignUpResponse

/**
 * 계정 API: 회원가입(POST /auth/signup), 로그인(POST /auth/login), 회원 탈퇴(DELETE /me).
 * 인터페이스로 두어 ViewModel을 테스트에서 fake로 대체할 수 있게 한다(의존성 역전).
 */
interface AccountApi {
    suspend fun signUp(request: SignUpRequest): ApiResult<SignUpResponse>
    suspend fun login(request: LoginRequest): ApiResult<LoginResponse>
    suspend fun logout(): ApiResult<Unit>
    suspend fun deleteAccount(): ApiResult<Unit>
}

class AccountApiImpl(private val client: ApiClient) : AccountApi {

    // 인증 엔드포인트는 401 자동 refresh-재시도 대상이 아니다(authRetry=false).
    override suspend fun signUp(request: SignUpRequest): ApiResult<SignUpResponse> =
        client.safeRequest(decode = { it.body<SignUpResponse>() }, authRetry = false) {
            client.http.post(client.url("/auth/signup")) {
                setBody(request)
            }
        }

    override suspend fun login(request: LoginRequest): ApiResult<LoginResponse> =
        client.safeRequest(decode = { it.body<LoginResponse>() }, authRetry = false) {
            client.http.post(client.url("/auth/login")) {
                setBody(request)
            }
        }

    /** 로그아웃. 서버가 토큰을 폐기하고 쿠키를 비운다(204). */
    override suspend fun logout(): ApiResult<Unit> =
        client.safeRequest(decode = { }, authRetry = false) {
            client.http.post(client.url("/auth/logout"))
        }

    /** 회원 탈퇴. 204 No Content이므로 본문을 디코드하지 않는다. */
    override suspend fun deleteAccount(): ApiResult<Unit> =
        client.safeRequest(decode = { }) {
            client.http.delete(client.url("/me")) {
                with(client) { withUser() }
            }
        }
}
