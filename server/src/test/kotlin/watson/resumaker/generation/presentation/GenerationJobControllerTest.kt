package watson.resumaker.generation.presentation

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.GenerationJobService
import watson.resumaker.generation.domain.GenerationJobStatus
import java.util.UUID

/**
 * [GenerationJobController] @WebMvcTest. 서비스는 모킹(슬라이스). 전역 예외 핸들러가 함께 로드되어
 * 미존재(404)·활성작업 삭제(409)·잘못된 식별자(400) 매핑을 함께 검증한다.
 */
@WebMvcTest(GenerationJobController::class)
class GenerationJobControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var generationJobService: GenerationJobService

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    private val jobId = UUID.randomUUID().toString()

    private fun jobResponse(status: GenerationJobStatus, artifactId: String? = null) = GenerationJobResponse(
        jobId = jobId,
        kind = ArtifactKind.RESUME,
        status = status,
        artifactId = artifactId,
        errorCode = null,
        errorMessage = null,
        targetCompany = "토스",
        createdAt = "2026-06-22T00:00:00Z",
    )

    @Test
    fun 작업_목록을_200으로_반환한다() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationJobService.list(any())).thenReturn(listOf(jobResponse(GenerationJobStatus.PENDING)))

        // when and then
        mockMvc.get("/generation-jobs")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].jobId") { value(jobId) }
                jsonPath("$[0].status") { value("PENDING") }
            }
    }

    @Test
    fun 단건_조회를_200으로_반환한다() {
        // given — 완료된 작업이면 artifactId가 실린다.
        val artifactId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationJobService.get(any(), any()))
            .thenReturn(jobResponse(GenerationJobStatus.SUCCEEDED, artifactId))

        // when and then
        mockMvc.get("/generation-jobs/$jobId")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("SUCCEEDED") }
                jsonPath("$.artifactId") { value(artifactId) }
            }
    }

    @Test
    fun 타인_소유_또는_미존재_조회는_404() {
        // given (소유 격리)
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(ResourceNotFoundException("요청하신 생성 작업을 찾을 수 없어요."))
            .whenever(generationJobService).get(any(), any())

        // when and then
        mockMvc.get("/generation-jobs/$jobId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun 잘못된_식별자_형식은_400() {
        // given — UUID 파싱 실패는 400.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))

        // when and then
        mockMvc.get("/generation-jobs/not-a-uuid")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
    }

    @Test
    fun 종료된_작업_삭제는_204() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))

        // when and then
        mockMvc.delete("/generation-jobs/$jobId")
            .andExpect {
                status { isNoContent() }
            }
    }

    @Test
    fun 활성_작업_삭제는_409() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(ConflictException("생성 중인 작업은 삭제할 수 없어요."))
            .whenever(generationJobService).delete(any(), any())

        // when and then
        mockMvc.delete("/generation-jobs/$jobId")
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("CONFLICT") }
            }
    }
}
