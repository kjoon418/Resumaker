package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.CreateTargetRequest
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.UpdateTargetRequest

/**
 * 목표 정보 API: GET/POST/PATCH/DELETE /targets. 모든 요청은 `X-User-Id`로 보호된다.
 * 인터페이스로 두어 ViewModel 테스트에서 fake로 대체할 수 있게 한다(의존성 역전).
 */
interface TargetApi {
    suspend fun getAll(): ApiResult<List<TargetResponse>>
    suspend fun getOne(id: String): ApiResult<TargetResponse>
    suspend fun create(request: CreateTargetRequest): ApiResult<TargetResponse>
    suspend fun update(id: String, request: UpdateTargetRequest): ApiResult<TargetResponse>
    suspend fun delete(id: String): ApiResult<Unit>
}

class TargetApiImpl(private val client: ApiClient) : TargetApi {

    override suspend fun getAll(): ApiResult<List<TargetResponse>> =
        client.safeRequest(decode = { it.body<List<TargetResponse>>() }) {
            client.http.get(client.url("/targets")) {
                with(client) { withUser() }
            }
        }

    override suspend fun getOne(id: String): ApiResult<TargetResponse> =
        client.safeRequest(decode = { it.body<TargetResponse>() }) {
            client.http.get(client.url("/targets/$id")) {
                with(client) { withUser() }
            }
        }

    override suspend fun create(request: CreateTargetRequest): ApiResult<TargetResponse> =
        client.safeRequest(decode = { it.body<TargetResponse>() }) {
            client.http.post(client.url("/targets")) {
                with(client) { withUser() }
                setBody(request)
            }
        }

    override suspend fun update(id: String, request: UpdateTargetRequest): ApiResult<TargetResponse> =
        client.safeRequest(decode = { it.body<TargetResponse>() }) {
            client.http.patch(client.url("/targets/$id")) {
                with(client) { withUser() }
                setBody(request)
            }
        }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.safeRequest(decode = { }) {
            client.http.delete(client.url("/targets/$id")) {
                with(client) { withUser() }
            }
        }
}
