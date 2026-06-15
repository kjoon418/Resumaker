package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.CreateTemplateRequest
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.dto.UpdateTemplateRequest

/**
 * 이력서 양식 API: GET/POST/PATCH/DELETE /resume-templates(FU-A). 모든 요청은 `X-User-Id`로 보호된다.
 * 인터페이스로 두어 ViewModel 테스트에서 fake로 대체할 수 있게 한다(의존성 역전).
 */
interface TemplateApi {
    suspend fun getAll(): ApiResult<List<TemplateResponse>>
    suspend fun getOne(id: String): ApiResult<TemplateResponse>
    suspend fun create(request: CreateTemplateRequest): ApiResult<TemplateResponse>
    suspend fun update(id: String, request: UpdateTemplateRequest): ApiResult<TemplateResponse>
    suspend fun delete(id: String): ApiResult<Unit>
}

class TemplateApiImpl(private val client: ApiClient) : TemplateApi {

    override suspend fun getAll(): ApiResult<List<TemplateResponse>> =
        client.safeRequest(decode = { it.body<List<TemplateResponse>>() }) {
            client.http.get(client.url("/resume-templates")) {
                with(client) { withUser() }
            }
        }

    override suspend fun getOne(id: String): ApiResult<TemplateResponse> =
        client.safeRequest(decode = { it.body<TemplateResponse>() }) {
            client.http.get(client.url("/resume-templates/$id")) {
                with(client) { withUser() }
            }
        }

    override suspend fun create(request: CreateTemplateRequest): ApiResult<TemplateResponse> =
        client.safeRequest(decode = { it.body<TemplateResponse>() }) {
            client.http.post(client.url("/resume-templates")) {
                with(client) { withUser() }
                setBody(request)
            }
        }

    override suspend fun update(id: String, request: UpdateTemplateRequest): ApiResult<TemplateResponse> =
        client.safeRequest(decode = { it.body<TemplateResponse>() }) {
            client.http.patch(client.url("/resume-templates/$id")) {
                with(client) { withUser() }
                setBody(request)
            }
        }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.safeRequest(decode = { }) {
            client.http.delete(client.url("/resume-templates/$id")) {
                with(client) { withUser() }
            }
        }
}
