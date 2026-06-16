package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.InterpretRequest
import watson.resumaker.model.dto.InterpretResponse
import watson.resumaker.model.dto.TemplatePresetResponse

/**
 * 프리셋 양식 목록 API(FU-B). 인증 불필요(서비스 제공 데이터).
 */
interface TemplatePresetApi {
    suspend fun getAll(): ApiResult<List<TemplatePresetResponse>>
}

class TemplatePresetApiImpl(private val client: ApiClient) : TemplatePresetApi {
    override suspend fun getAll(): ApiResult<List<TemplatePresetResponse>> =
        client.safeRequest(decode = { it.body<List<TemplatePresetResponse>>() }) {
            client.http.get(client.url("/resume-templates/presets"))
        }
}

/**
 * 회사 양식 붙여넣기 해석 API(FU-C). 영속하지 않으며, 결과는 후보 섹션만 반환한다.
 * 확정은 [TemplateApi.create]로 수행한다.
 */
interface TemplateInterpretApi {
    suspend fun interpret(request: InterpretRequest): ApiResult<InterpretResponse>
}

class TemplateInterpretApiImpl(private val client: ApiClient) : TemplateInterpretApi {
    override suspend fun interpret(request: InterpretRequest): ApiResult<InterpretResponse> =
        client.safeRequest(decode = { it.body<InterpretResponse>() }) {
            client.http.post(client.url("/resume-templates/interpret")) {
                setBody(request)
            }
        }
}
