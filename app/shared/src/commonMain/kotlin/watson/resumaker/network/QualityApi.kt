package watson.resumaker.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import watson.resumaker.model.dto.AdoptCandidatesRequest
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.QualityImprovementJobResponse
import watson.resumaker.model.dto.QualityImprovementRequest
import watson.resumaker.model.dto.QualityReviewRequest
import watson.resumaker.model.dto.QualityReviewResponse

/**
 * 품질 점검·개선 API. 모든 요청은 `X-User-Id`로 보호된다(ApiClient 주입).
 *
 * 흐름:
 *  1. [reviewQuality]   — 동기 점검(소견 목록). 무비용에 가깝고 폴링 없음.
 *  2. [submitImprovement] — 자동 적용 소견으로 비동기 개선 작업 접수(202). AUTO_REWRITE만.
 *  3. [getImprovementJob] — 작업 폴링(기존 생성 잡 폴링과 동형, 3초 간격).
 *  4. [adoptCandidates]  — 채택(일괄). 서버가 새 활성 버전을 만들고 ArtifactResponse로 반환.
 *
 * 포트폴리오 산출물은 점검/개선 엔드포인트가 400을 반환한다(서버 QC10). 화면이 RESUME에만 진입점을 노출해야 한다.
 */
interface QualityApi {
    /**
     * 품질 점검(동기, POST /artifacts/{id}/quality-review → 200).
     * 소견 0건이어도 200으로 내려온다(빈 findings).
     * 포트폴리오에는 400([ApiResult.Failure], code=UNSUPPORTED_ARTIFACT_KIND).
     */
    suspend fun reviewQuality(artifactId: String): ApiResult<QualityReviewResponse>

    /**
     * 품질 개선 작업 접수(POST /artifacts/{id}/quality-improvements → 202).
     * [findingIds]에는 AUTO_REWRITE 소견 id만 포함한다. 빈 목록이면 400.
     * 일일 한도 초과 시 429(code=QUALITY_IMPROVEMENT_QUOTA_EXCEEDED).
     */
    suspend fun submitImprovement(
        artifactId: String,
        findingIds: List<String>,
    ): ApiResult<QualityImprovementJobResponse>

    /**
     * 품질 개선 작업 폴링(GET /artifacts/{id}/quality-improvements/{jobId} → 200).
     * [QualityImprovementJobResponse.status]가 SUCCEEDED|FAILED면 폴링을 멈춘다.
     * SUCCEEDED일 때만 [QualityImprovementJobResponse.candidates]가 채워진다.
     */
    suspend fun getImprovementJob(
        artifactId: String,
        jobId: String,
    ): ApiResult<QualityImprovementJobResponse>

    /**
     * 후보 채택(POST /artifacts/{id}/quality-improvements/{jobId}/adopt → 200).
     * 선택된 [candidateIds]를 새 활성 버전에 반영한 [ArtifactResponse]를 반환한다.
     * 일괄 채택은 한 번의 버전 전이로 묶인다(버전 폭증 방지 — 도메인 §3.5).
     */
    suspend fun adoptCandidates(
        artifactId: String,
        jobId: String,
        candidateIds: List<String>,
    ): ApiResult<ArtifactResponse>

    /**
     * 산출물의 가장 최근 품질 개선 작업 조회(GET /artifacts/{id}/quality-improvements/latest).
     * 작업이 없으면 204 → Success(null). 산출물 열람 화면이 비차단 진행 카드를 복원할 때 쓴다(§3).
     */
    suspend fun getLatestImprovement(artifactId: String): ApiResult<QualityImprovementJobResponse?>

    /**
     * 품질 개선 작업 닫기(DELETE /artifacts/{id}/quality-improvements/{jobId} → 204).
     * 진행 카드의 "닫기"로 실패·미채택 작업을 치울 때 쓴다.
     */
    suspend fun dismissImprovement(artifactId: String, jobId: String): ApiResult<Unit>
}

class QualityApiImpl(private val client: ApiClient) : QualityApi {

    override suspend fun reviewQuality(artifactId: String): ApiResult<QualityReviewResponse> =
        client.safeRequest(decode = { it.body<QualityReviewResponse>() }) {
            client.http.post(client.url("/artifacts/$artifactId/quality-review")) {
                with(client) { withUser() }
                setBody(QualityReviewRequest())
            }
        }

    override suspend fun submitImprovement(
        artifactId: String,
        findingIds: List<String>,
    ): ApiResult<QualityImprovementJobResponse> =
        client.safeRequest(decode = { it.body<QualityImprovementJobResponse>() }) {
            client.http.post(client.url("/artifacts/$artifactId/quality-improvements")) {
                with(client) { withUser() }
                setBody(QualityImprovementRequest(findingIds = findingIds))
            }
        }

    override suspend fun getImprovementJob(
        artifactId: String,
        jobId: String,
    ): ApiResult<QualityImprovementJobResponse> =
        client.safeRequest(decode = { it.body<QualityImprovementJobResponse>() }) {
            client.http.get(client.url("/artifacts/$artifactId/quality-improvements/$jobId")) {
                with(client) { withUser() }
            }
        }

    override suspend fun adoptCandidates(
        artifactId: String,
        jobId: String,
        candidateIds: List<String>,
    ): ApiResult<ArtifactResponse> =
        client.safeRequest(decode = { it.body<ArtifactResponse>() }) {
            client.http.post(client.url("/artifacts/$artifactId/quality-improvements/$jobId/adopt")) {
                with(client) { withUser() }
                setBody(AdoptCandidatesRequest(candidateIds = candidateIds))
            }
        }

    override suspend fun getLatestImprovement(artifactId: String): ApiResult<QualityImprovementJobResponse?> =
        // 작업이 없으면 서버가 204를 준다(본문 없음) → null. 200이면 본문을 파싱한다.
        client.safeRequest(decode = { if (it.status == HttpStatusCode.NoContent) null else it.body<QualityImprovementJobResponse>() }) {
            client.http.get(client.url("/artifacts/$artifactId/quality-improvements/latest")) {
                with(client) { withUser() }
            }
        }

    override suspend fun dismissImprovement(artifactId: String, jobId: String): ApiResult<Unit> =
        client.safeRequest(decode = { }) {
            client.http.delete(client.url("/artifacts/$artifactId/quality-improvements/$jobId")) {
                with(client) { withUser() }
            }
        }
}
