package watson.resumaker.quality.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.quality.application.QualityImprovementJobService
import watson.resumaker.quality.application.QualityReviewService
import watson.resumaker.quality.domain.Finding
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.QualityImprovementJobStatus
import watson.resumaker.quality.domain.QualityReport
import watson.resumaker.quality.domain.SuggestionGuide
import watson.resumaker.quality.domain.TreatmentKind
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

/**
 * [QualityController] @WebMvcTest. 서비스·매퍼는 슬라이스로 로드하고, 전역 예외 핸들러가 함께 로드돼 거절 매핑을
 * 검증한다. 진단·처치 접수·처치 조회를 본다(채택은 후속 커밋).
 *
 * 검증: 200 + 소견 목록, QC10 포트폴리오 거절(400), QC8 소유 격리(404).
 */
@WebMvcTest(QualityController::class)
@Import(QualityMapper::class)
class QualityControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var reviewService: QualityReviewService

    @MockitoBean
    private lateinit var improvementJobService: QualityImprovementJobService

    @MockitoBean
    private lateinit var adoptionService: watson.resumaker.quality.application.CandidateAdoptionService

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    private val artifactId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val sectionId = SectionId(UUID.randomUUID())

    @Test
    fun 품질_점검은_200과_소견_목록을_반환한다() {
        // given — AUTO_REWRITE 소견 1 + SUGGESTION 소견 1.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(reviewService.review(any(), any())).thenReturn(
            QualityReport(
                artifactId = artifactId,
                versionId = versionId,
                findings = listOf(
                    Finding(
                        findingId = "${sectionId.value}:I1",
                        sectionId = sectionId,
                        definitionKey = "section-0-요약",
                        criterion = QualityCriterion.STRONG_VERB,
                        treatmentKind = TreatmentKind.AUTO_REWRITE,
                        evidenceText = "담당했다",
                    ),
                    Finding(
                        findingId = "${sectionId.value}:I4",
                        sectionId = sectionId,
                        definitionKey = "section-0-요약",
                        criterion = QualityCriterion.VAGUE_METRIC,
                        treatmentKind = TreatmentKind.SUGGESTION,
                        evidenceText = "대용량",
                        suggestionGuide = SuggestionGuide("구체 값을 적어 주세요.", ExperienceRecordId(UUID.randomUUID())),
                    ),
                ),
            ),
        )

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-review")
            .andExpect {
                status { isOk() }
                jsonPath("$.artifactId") { value(artifactId.toString()) }
                jsonPath("$.versionId") { value(versionId.toString()) }
                jsonPath("$.autoRewriteCount") { value(1) }
                jsonPath("$.findings[0].criterionId") { value("I1") }
                jsonPath("$.findings[0].treatmentKind") { value("AUTO_REWRITE") }
                jsonPath("$.findings[1].treatmentKind") { value("SUGGESTION") }
                jsonPath("$.findings[1].suggestionGuide.message") { value("구체 값을 적어 주세요.") }
            }
    }

    @Test
    fun 포트폴리오_산출물_점검은_400을_반환한다() {
        // given (QC10) — 서비스가 RESUME 가드로 거절(DomainValidationException → 400).
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(DomainValidationException("품질 점검은 이력서 산출물에서만 사용할 수 있어요."))
            .whenever(reviewService).review(any(), any())

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-review")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun 타인_소유이거나_미존재_산출물_점검은_404를_반환한다() {
        // given (QC8) — 미존재·타인 모두 404.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요."))
            .whenever(reviewService).review(any(), any())

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-review")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun 품질_개선_접수는_202와_jobId를_반환한다() {
        // given — AUTO_REWRITE 소견을 골라 접수하면 PENDING 작업이 만들어진다.
        val jobId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(improvementJobService.submit(any(), any(), any())).thenReturn(
            QualityImprovementJobResponse(
                jobId = jobId,
                status = QualityImprovementJobStatus.PENDING,
                candidates = null,
                errorCode = null,
                errorMessage = null,
                createdAt = "2026-06-22T00:00:00Z",
            ),
        )
        val request = QualityImprovementRequest(findingIds = listOf("${sectionId.value}:I1"))

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-improvements") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.jobId") { value(jobId) }
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun 빈_소견_목록_접수는_400을_반환한다() {
        // given (형식 검증) — findingIds 비어 있으면 @NotEmpty로 거부.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        val request = QualityImprovementRequest(findingIds = emptyList())

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-improvements") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.field") { value("findingIds") }
        }
    }

    @Test
    fun 품질_개선_작업_조회는_200과_후보를_반환한다() {
        // given — 성공한 작업의 후보를 비교용으로 내려준다.
        val jobId = UUID.randomUUID()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(improvementJobService.get(any(), any())).thenReturn(
            QualityImprovementJobResponse(
                jobId = jobId.toString(),
                status = QualityImprovementJobStatus.SUCCEEDED,
                candidates = listOf(
                    CandidateDto(
                        candidateId = UUID.randomUUID().toString(),
                        sectionId = sectionId.value.toString(),
                        definitionKey = "section-0-요약",
                        originalContent = "결제를 담당했다.",
                        candidateContent = "결제 시스템을 설계·운영했어요.",
                        appliedCriterionIds = listOf("I1"),
                    ),
                ),
                errorCode = null,
                errorMessage = null,
                createdAt = "2026-06-22T00:00:00Z",
            ),
        )

        // when and then
        mockMvc.get("/artifacts/$artifactId/quality-improvements/$jobId")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("SUCCEEDED") }
                jsonPath("$.candidates[0].candidateContent") { value("결제 시스템을 설계·운영했어요.") }
                jsonPath("$.candidates[0].appliedCriterionIds[0]") { value("I1") }
            }
    }

    @Test
    fun 자동적용_소견이_없는_접수는_400을_반환한다() {
        // given — 개선 제안만 골라 접수하면 서비스가 거절(DomainValidationException → 400).
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(DomainValidationException("다듬을 수 있는 소견이 없어요. 품질 점검을 다시 해 주세요."))
            .whenever(improvementJobService).submit(any(), any(), any())
        val request = QualityImprovementRequest(findingIds = listOf("${sectionId.value}:I4"))

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-improvements") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun 최신_개선_작업_조회는_200과_작업을_반환한다() {
        // given (§3) — 산출물 열람 화면이 비차단 진행 카드를 복원할 때 최신 작업을 본다.
        val jobId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(improvementJobService.latestFor(any(), any())).thenReturn(
            QualityImprovementJobResponse(
                jobId = jobId,
                status = QualityImprovementJobStatus.RUNNING,
                candidates = null,
                errorCode = null,
                errorMessage = null,
                createdAt = "2026-06-22T00:00:00Z",
            ),
        )

        // when and then — 리터럴 경로 `latest`가 `{jobId}` 패턴보다 먼저 매칭된다.
        mockMvc.get("/artifacts/$artifactId/quality-improvements/latest")
            .andExpect {
                status { isOk() }
                jsonPath("$.jobId") { value(jobId) }
                jsonPath("$.status") { value("RUNNING") }
            }
    }

    @Test
    fun 최신_개선_작업이_없으면_204를_반환한다() {
        // given — 작업이 없으면 204(No Content) → 화면이 카드를 띄우지 않는다.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(improvementJobService.latestFor(any(), any())).thenReturn(null)

        // when and then
        mockMvc.get("/artifacts/$artifactId/quality-improvements/latest")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun 개선_작업_닫기는_204를_반환한다() {
        // given — 진행 카드 "닫기": 작업·후보를 지우고 204.
        val jobId = UUID.randomUUID()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))

        // when and then
        mockMvc.delete("/artifacts/$artifactId/quality-improvements/$jobId")
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun 후보_채택은_200과_갱신된_활성_버전을_반환한다() {
        // given — 일괄 채택으로 새 활성 버전이 생긴다.
        val jobId = UUID.randomUUID()
        val newVersionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(adoptionService.adopt(any(), any(), any(), any())).thenReturn(
            watson.resumaker.generation.presentation.ArtifactResponse(
                id = artifactId.toString(),
                kind = watson.resumaker.artifact.domain.ArtifactKind.RESUME,
                activeVersion = watson.resumaker.generation.presentation.ArtifactVersionResponse(
                    versionId = newVersionId,
                    sections = listOf(
                        watson.resumaker.generation.presentation.ArtifactSectionResponse(
                            id = UUID.randomUUID().toString(),
                            sectionKind = watson.resumaker.artifact.domain.SectionKind.SUMMARY,
                            definitionKey = "section-0-요약",
                            content = "다듬은 요약",
                            status = watson.resumaker.artifact.domain.SectionStatus.GENERATED,
                            sourceExperienceIds = emptyList(),
                        ),
                    ),
                ),
            ),
        )
        val request = AdoptCandidatesRequest(candidateIds = listOf(UUID.randomUUID().toString()))

        // when and then
        mockMvc.post("/artifacts/$artifactId/quality-improvements/$jobId/adopt") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.activeVersion.versionId") { value(newVersionId) }
            jsonPath("$.activeVersion.sections[0].content") { value("다듬은 요약") }
        }
    }
}
