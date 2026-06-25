package watson.resumaker.experience.presentation

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.application.ExperienceService
import watson.resumaker.experience.domain.ExperienceReviewCriterion
import watson.resumaker.experience.domain.ExperienceReviewField
import watson.resumaker.experience.domain.ExperienceType
import java.util.UUID

/**
 * [ExperienceController] @WebMvcTest — 경험 점검 엔드포인트와 응답의 boostHintCount 직렬화를 본다.
 *
 * 검증: GET /experiences/{id}/review 200 + 소견 목록, 소유 격리 404, 상세 응답에 boostHintCount 포함.
 */
@WebMvcTest(ExperienceController::class)
@Import(ExperienceMapper::class)
class ExperienceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ExperienceService

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    private val experienceId = UUID.randomUUID()

    @Test
    fun 경험_점검은_200과_보강_소견_목록을_반환한다() {
        // given — 모호 수치 보강 유도 1건.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(service.review(any(), any())).thenReturn(
            ExperienceReviewResponse(
                findings = listOf(
                    ExperienceReviewFindingDto(
                        criterion = ExperienceReviewCriterion.VAGUE_METRIC,
                        field = ExperienceReviewField.BODY,
                        message = "구체적인 수치를 적어보세요.",
                        evidenceText = "대용량",
                    ),
                ),
            ),
        )

        // when and then
        mockMvc.get("/experiences/$experienceId/review")
            .andExpect {
                status { isOk() }
                jsonPath("$.findings[0].criterion") { value("VAGUE_METRIC") }
                jsonPath("$.findings[0].field") { value("BODY") }
                jsonPath("$.findings[0].evidenceText") { value("대용량") }
            }
    }

    @Test
    fun 타인_소유이거나_미존재_경험_점검은_404를_반환한다() {
        // given — findByIdAndOwnerId가 null이면 서비스가 404.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(ResourceNotFoundException("요청하신 경험 기록을 찾을 수 없어요."))
            .whenever(service).review(any(), any())

        // when and then
        mockMvc.get("/experiences/$experienceId/review")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun 경험_상세는_boostHintCount를_포함한다() {
        // given — 점검이 2건 보강을 추천한 경험.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(service.getOne(any(), any())).thenReturn(
            ExperienceResponse(
                id = experienceId.toString(),
                title = "결제 시스템 개편",
                type = ExperienceType.PROJECT,
                body = "대용량 트래픽을 처리했습니다.",
                situation = null,
                action = null,
                result = null,
                periodStart = null,
                periodEnd = null,
                skillTags = emptyList(),
                boostHintCount = 2,
            ),
        )

        // when and then
        mockMvc.get("/experiences/$experienceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.boostHintCount") { value(2) }
            }
    }
}
