package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.EditSectionContentRequest
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.dto.PortfolioGenerationRequest
import watson.resumaker.model.dto.RegenerateSectionRequest
import watson.resumaker.model.dto.ResumeGenerationRequest

/**
 * 산출물 API: 생성(이력서/포트폴리오)·열람. 모든 요청은 `X-User-Id`로 보호된다(ApiClient 주입).
 * 인터페이스로 두어 ViewModel 테스트에서 fake로 대체할 수 있게 한다(의존성 역전).
 *
 * 부분 성공(일부 항목 *_FAILED, 서버 200)도 [ApiResult.Success]로 받아 화면이 항목 상태로 고지한다
 * (도메인 이해 §306·신뢰성 가드레일 — 가짜 성공 금지). 빈 경험(409 ADD_EXPERIENCE)·타인/미존재(404)는
 * [ApiResult.Failure]로 내려오며 `code`로 분기한다.
 *
 * Slice 2(항목 재생성·직접 편집)는 아래 메서드로 추가됐다. 재생성은 LLM 호출(수십 초)이라 withLongTimeout을
 * 적용하고, 편집은 AI 비호출 동기 영속이라 일반 타임아웃을 쓴다. 둘 다 [ArtifactResponse](활성 버전 래핑)로
 * 응답을 받아 화면을 갱신한다. 동시 재생성(409 CONFLICT)·미존재/타인(404 NOT_FOUND)·빈 편집 내용(400
 * INVALID_REQUEST)은 [ApiResult.Failure]의 `code`로 분기한다.
 *
 * Slice 3(버전 목록 GET .../versions, 복원 POST .../versions/{id}/restore)는 이 인터페이스에 메서드를
 * 추가하는 형태로 깔끔히 올라탄다(이번 슬라이스 범위 밖 — 시그니처만 이 주석으로 시밍).
 */
interface ArtifactApi {
    suspend fun generateResume(request: ResumeGenerationRequest): ApiResult<GenerationResponse>
    suspend fun generatePortfolio(request: PortfolioGenerationRequest): ApiResult<GenerationResponse>
    suspend fun getArtifact(id: String): ApiResult<ArtifactResponse>

    /**
     * 항목 단위 재생성(POST .../sections/{sectionId}/regenerate). 단일 항목만 AI로 다시 만들어 새 활성 버전을
     * 만든 갱신 산출물을 돌려준다. LLM 호출이라 withLongTimeout 적용. 같은 항목 동시 재생성은 409(CONFLICT)로
     * 거절되고, 부분 성공(재생성 항목이 *_FAILED)도 200으로 내려와 [ApiResult.Success]가 된다(항목 상태로 고지).
     */
    suspend fun regenerateSection(
        artifactId: String,
        sectionId: String,
        directive: String?,
    ): ApiResult<ArtifactResponse>

    /**
     * 항목 직접 편집(PUT .../sections/{sectionId}/content). 사용자가 작성한 내용으로 항목을 교체한 새 활성 버전을
     * 만든 갱신 산출물을 돌려준다. 자동 검증 미적용(§428)이라 항상 사용자 내용이 그대로 반영된다. 빈 내용은
     * 400(INVALID_REQUEST)로 거절된다(클라이언트에서도 빈 입력 시 저장을 막는다).
     */
    suspend fun editSectionContent(
        artifactId: String,
        sectionId: String,
        content: String,
    ): ApiResult<ArtifactResponse>
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

    override suspend fun regenerateSection(
        artifactId: String,
        sectionId: String,
        directive: String?,
    ): ApiResult<ArtifactResponse> =
        client.safeRequest(decode = { it.body<ArtifactResponse>() }) {
            client.http.post(client.url("/artifacts/$artifactId/sections/$sectionId/regenerate")) {
                with(client) {
                    withUser()
                    withLongTimeout()
                }
                setBody(RegenerateSectionRequest(directive = directive))
            }
        }

    override suspend fun editSectionContent(
        artifactId: String,
        sectionId: String,
        content: String,
    ): ApiResult<ArtifactResponse> =
        client.safeRequest(decode = { it.body<ArtifactResponse>() }) {
            client.http.put(client.url("/artifacts/$artifactId/sections/$sectionId/content")) {
                with(client) { withUser() }
                setBody(EditSectionContentRequest(content = content))
            }
        }
}
