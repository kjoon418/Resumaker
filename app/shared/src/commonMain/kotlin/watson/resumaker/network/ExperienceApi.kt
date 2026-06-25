package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.CreateExperienceRequest
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.ExperienceReviewResponse
import watson.resumaker.model.dto.UpdateExperienceRequest

/**
 * 경험 기록 API: GET/POST/PATCH/DELETE /experiences. 모든 요청은 `X-User-Id`로 보호된다.
 * 인터페이스로 두어 ViewModel 테스트에서 fake로 대체할 수 있게 한다(의존성 역전).
 */
interface ExperienceApi {
    suspend fun getAll(): ApiResult<List<ExperienceResponse>>
    suspend fun getOne(id: String): ApiResult<ExperienceResponse>
    suspend fun create(request: CreateExperienceRequest): ApiResult<ExperienceResponse>
    suspend fun update(id: String, request: UpdateExperienceRequest): ApiResult<ExperienceResponse>
    suspend fun delete(id: String): ApiResult<Unit>

    /** 경험 점검(결정적 보강 유도). 편집 화면 점검 패널이 무엇을 더 적을지 안내하는 데 쓴다. */
    suspend fun review(id: String): ApiResult<ExperienceReviewResponse>
}

class ExperienceApiImpl(private val client: ApiClient) : ExperienceApi {

    override suspend fun getAll(): ApiResult<List<ExperienceResponse>> =
        client.safeRequest(decode = { it.body<List<ExperienceResponse>>() }) {
            client.http.get(client.url("/experiences")) {
                with(client) { withUser() }
            }
        }

    override suspend fun getOne(id: String): ApiResult<ExperienceResponse> =
        client.safeRequest(decode = { it.body<ExperienceResponse>() }) {
            client.http.get(client.url("/experiences/$id")) {
                with(client) { withUser() }
            }
        }

    override suspend fun create(request: CreateExperienceRequest): ApiResult<ExperienceResponse> =
        client.safeRequest(decode = { it.body<ExperienceResponse>() }) {
            client.http.post(client.url("/experiences")) {
                with(client) { withUser() }
                setBody(request)
            }
        }

    override suspend fun update(id: String, request: UpdateExperienceRequest): ApiResult<ExperienceResponse> =
        client.safeRequest(decode = { it.body<ExperienceResponse>() }) {
            client.http.patch(client.url("/experiences/$id")) {
                with(client) { withUser() }
                setBody(request)
            }
        }

    override suspend fun delete(id: String): ApiResult<Unit> =
        client.safeRequest(decode = { }) {
            client.http.delete(client.url("/experiences/$id")) {
                with(client) { withUser() }
            }
        }

    override suspend fun review(id: String): ApiResult<ExperienceReviewResponse> =
        client.safeRequest(decode = { it.body<ExperienceReviewResponse>() }) {
            client.http.get(client.url("/experiences/$id/review")) {
                with(client) { withUser() }
            }
        }
}
