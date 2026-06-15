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
    suspend fun deleteAccount(): ApiResult<Unit>
}

class AccountApiImpl(private val client: ApiClient) : AccountApi {

    override suspend fun signUp(request: SignUpRequest): ApiResult<SignUpResponse> =
        client.safeRequest(decode = { it.body<SignUpResponse>() }) {
            client.http.post(client.url("/auth/signup")) {
                setBody(request)
            }
        }

    override suspend fun login(request: LoginRequest): ApiResult<LoginResponse> =
        client.safeRequest(decode = { it.body<LoginResponse>() }) {
            client.http.post(client.url("/auth/login")) {
                setBody(request)
            }
        }

    /** 회원 탈퇴. 204 No Content이므로 본문을 디코드하지 않는다. */
    override suspend fun deleteAccount(): ApiResult<Unit> =
        client.safeRequest(decode = { }) {
            client.http.delete(client.url("/me")) {
                with(client) { withUser() }
            }
        }
}
