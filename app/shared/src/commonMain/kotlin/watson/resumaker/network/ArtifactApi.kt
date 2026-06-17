package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.dto.PortfolioGenerationRequest
import watson.resumaker.model.dto.ResumeGenerationRequest

/**
 * 산출물 API: 생성(이력서/포트폴리오)·열람. 모든 요청은 `X-User-Id`로 보호된다(ApiClient 주입).
 * 인터페이스로 두어 ViewModel 테스트에서 fake로 대체할 수 있게 한다(의존성 역전).
 *
 * 부분 성공(일부 항목 *_FAILED, 서버 200)도 [ApiResult.Success]로 받아 화면이 항목 상태로 고지한다
 * (도메인 이해 §306·신뢰성 가드레일 — 가짜 성공 금지). 빈 경험(409 ADD_EXPERIENCE)·타인/미존재(404)는
 * [ApiResult.Failure]로 내려오며 `code`로 분기한다.
 *
 * Slice 2(항목 재생성 POST .../sections/{id}/regenerate, 직접 편집 PUT .../sections/{id}/content)와
 * Slice 3(버전 목록 GET .../versions, 복원 POST .../versions/{id}/restore)는 이 인터페이스에 메서드를
 * 추가하는 형태로 깔끔히 올라탄다(이번 슬라이스 범위 밖 — 시그니처만 위 주석으로 시밍).
 */
interface ArtifactApi {
    suspend fun generateResume(request: ResumeGenerationRequest): ApiResult<GenerationResponse>
    suspend fun generatePortfolio(request: PortfolioGenerationRequest): ApiResult<GenerationResponse>
    suspend fun getArtifact(id: String): ApiResult<ArtifactResponse>
}

class ArtifactApiImpl(private val client: ApiClient) : ArtifactApi {

    override suspend fun generateResume(request: ResumeGenerationRequest): ApiResult<GenerationResponse> =
        client.safeRequest(decode = { it.body<GenerationResponse>() }) {
            client.http.post(client.url("/artifacts/resume")) {
                with(client) {
                    withUser()
                    withLongTimeout()
                }
                setBody(request)
            }
        }

    override suspend fun generatePortfolio(request: PortfolioGenerationRequest): ApiResult<GenerationResponse> =
        client.safeRequest(decode = { it.body<GenerationResponse>() }) {
            client.http.post(client.url("/artifacts/portfolio")) {
                with(client) {
                    withUser()
                    withLongTimeout()
                }
                setBody(request)
            }
        }

    override suspend fun getArtifact(id: String): ApiResult<ArtifactResponse> =
        client.safeRequest(decode = { it.body<ArtifactResponse>() }) {
            client.http.get(client.url("/artifacts/$id")) {
                with(client) { withUser() }
            }
        }
}
