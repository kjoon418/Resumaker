package watson.resumaker.generation.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.common.domain.EmptyExperienceSelectionException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.ArtifactGenerationService
import watson.resumaker.generation.application.ArtifactReadService
import java.util.UUID

/**
 * [ArtifactController] @WebMvcTest. 서비스·매퍼는 모킹한다(슬라이스). 전역 예외 핸들러가 함께 로드되어
 * 빈 묶음(409)·미존재/타인(404)·필수 누락(400) 매핑을 함께 검증한다.
 */
@WebMvcTest(ArtifactController::class)
@Import(GenerationMapper::class)
class ArtifactControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var generationService: ArtifactGenerationService

    @MockitoBean
    private lateinit var readService: ArtifactReadService

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    private val expId = UUID.randomUUID().toString()
    private val targetId = UUID.randomUUID().toString()
    private val templateId = UUID.randomUUID().toString()
    private val artifactId = UUID.randomUUID().toString()

    private fun section(status: SectionStatus) = GeneratedSectionResponse(
        sectionId = UUID.randomUUID().toString(),
        definitionKey = "section-0-요약",
        sectionKind = SectionKind.SUMMARY,
        content = "요약 본문",
        status = status,
        sourceExperienceIds = listOf(expId),
        factGroundings = emptyList(),
    )

    private fun generationResponse(vararg statuses: SectionStatus) = GenerationResponse(
        artifactId = artifactId,
        kind = ArtifactKind.RESUME,
        activeVersionId = UUID.randomUUID().toString(),
        sections = statuses.map { section(it) },
    )

    @Test
    fun 이력서_생성이_모두_성공하면_201과_생성결과를_반환한다() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationService.generateResume(any(), any()))
            .thenReturn(generationResponse(SectionStatus.GENERATED))
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.artifactId") { value(artifactId) }
            jsonPath("$.sections[0].status") { value("GENERATED") }
        }
    }

    @Test
    fun 이력서_부분_실패_버전이면_200으로_내려준다() {
        // given (도메인 이해 §306)
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationService.generateResume(any(), any()))
            .thenReturn(generationResponse(SectionStatus.GENERATED, SectionStatus.GENERATION_FAILED))
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.sections[1].status") { value("GENERATION_FAILED") }
        }
    }

    @Test
    fun 검증_실패가_섞인_부분_실패_버전이면_200으로_내려준다() {
        // given (도메인 이해 §306) — VALIDATION_FAILED도 부분 실패(*_FAILED)로 200 처리됨을 회귀 고정.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationService.generateResume(any(), any()))
            .thenReturn(generationResponse(SectionStatus.GENERATED, SectionStatus.VALIDATION_FAILED))
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.sections[1].status") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun 포트폴리오_생성이_성공하면_201과_생성결과를_반환한다() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationService.generatePortfolio(any(), any()))
            .thenReturn(
                GenerationResponse(
                    artifactId = artifactId,
                    kind = ArtifactKind.PORTFOLIO,
                    activeVersionId = UUID.randomUUID().toString(),
                    sections = listOf(
                        GeneratedSectionResponse(
                            sectionId = UUID.randomUUID().toString(),
                            definitionKey = expId,
                            sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
                            content = "서사",
                            status = SectionStatus.GENERATED,
                            sourceExperienceIds = listOf(expId),
                            factGroundings = emptyList(),
                        ),
                    ),
                ),
            )
        val request = PortfolioGenerationRequest(listOf(expId), targetId)

        // when and then
        mockMvc.post("/artifacts/portfolio") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.kind") { value("PORTFOLIO") }
        }
    }

    @Test
    fun 이력서_요청에_경험이_비어있으면_400을_반환한다() {
        // given (필수 누락 — 형식 검증)
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        val request = ResumeGenerationRequest(emptyList(), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.field") { value("experienceIds") }
        }
    }

    @Test
    fun 이력서_요청에_양식이_누락되면_400을_반환한다() {
        // given (필수 누락)
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId = null)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.field") { value("templateId") }
        }
    }

    @Test
    fun 빈_경험_묶음_생성_충돌이면_409와_경험추가_액션을_반환한다() {
        // given (수용 기준 8) — 서비스가 EmptyExperienceSelectionException을 던지면 핸들러가 409+action으로 매핑한다.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationService.generateResume(any(), any()))
            .thenThrow(EmptyExperienceSelectionException("이력서·포트폴리오를 만들려면 경험을 하나 이상 골라 주세요."))
        // Bean Validation을 통과하도록 경험은 채우되, 서비스 단계에서 충돌을 던지는 경로를 검증한다.
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMPTY_EXPERIENCE_SELECTION") }
            jsonPath("$.action") { value("ADD_EXPERIENCE") }
        }
    }

    @Test
    fun 산출물을_열람하면_200과_활성_버전을_반환한다() {
        // given (수용 기준 12)
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(readService.getArtifact(any(), any())).thenReturn(
            ArtifactResponse(
                id = artifactId,
                kind = ArtifactKind.RESUME,
                activeVersion = ArtifactVersionResponse(
                    versionId = UUID.randomUUID().toString(),
                    sections = listOf(
                        ArtifactSectionResponse(
                            id = UUID.randomUUID().toString(),
                            sectionKind = SectionKind.SUMMARY,
                            definitionKey = "section-0-요약",
                            content = "요약 본문",
                            status = SectionStatus.GENERATED,
                            sourceExperienceIds = listOf(expId),
                        ),
                    ),
                ),
            ),
        )

        // when and then
        mockMvc.get("/artifacts/$artifactId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(artifactId) }
                jsonPath("$.activeVersion.sections[0].content") { value("요약 본문") }
                jsonPath("$.activeVersion.sections[0].sourceExperienceIds[0]") { value(expId) }
            }
    }

    @Test
    fun 타인_소유_산출물_열람은_404를_반환한다() {
        // given (소유 격리, 수용 기준 13) — 미존재·타인 모두 404.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(readService.getArtifact(any(), any()))
            .thenThrow(ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요."))

        // when and then
        mockMvc.get("/artifacts/$artifactId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }
}
