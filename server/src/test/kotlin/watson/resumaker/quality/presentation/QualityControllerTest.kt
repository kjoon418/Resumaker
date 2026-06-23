package watson.resumaker.quality.presentation

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.quality.application.QualityReviewService
import watson.resumaker.quality.domain.Finding
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.QualityReport
import watson.resumaker.quality.domain.SuggestionGuide
import watson.resumaker.quality.domain.TreatmentKind
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

/**
 * [QualityController] @WebMvcTest. 서비스·매퍼는 슬라이스로 로드하고, 전역 예외 핸들러가 함께 로드돼 거절 매핑을
 * 검증한다. 진단(품질 점검)만 본다(처치·채택은 후속 커밋).
 *
 * 검증: 200 + 소견 목록, QC10 포트폴리오 거절(400), QC8 소유 격리(404).
 */
@WebMvcTest(QualityController::class)
@Import(QualityMapper::class)
class QualityControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var reviewService: QualityReviewService

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
}
