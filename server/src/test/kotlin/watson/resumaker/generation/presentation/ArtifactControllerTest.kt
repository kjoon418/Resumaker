package watson.resumaker.generation.presentation

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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.QuotaExceededException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.ArtifactReadService
import watson.resumaker.generation.application.GenerationJobService
import watson.resumaker.generation.application.SectionEditService
import watson.resumaker.generation.application.SectionRegenerationService
import watson.resumaker.generation.application.VersionRestoreService
import watson.resumaker.generation.domain.GenerationJobStatus
import java.util.UUID

/**
 * [ArtifactController] @WebMvcTest. 서비스·매퍼는 모킹한다(슬라이스). 전역 예외 핸들러가 함께 로드되어
 * 한도초과(429)·미존재/타인(404)·필수 누락(400) 매핑을 함께 검증한다.
 *
 * **비동기 생성 전환:** POST /resume·/portfolio는 더 이상 동기 201/200을 주지 않고, 제출 즉시 **202+jobId**를 준다
 * (실제 생성은 워커가 백그라운드로 수행). 동기 생성 호출(ArtifactGenerationService)은 컨트롤러에서 사라졌다.
 */
@WebMvcTest(ArtifactController::class)
@Import(GenerationMapper::class)
class ArtifactControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var generationJobService: GenerationJobService

    @MockitoBean
    private lateinit var readService: ArtifactReadService

    @MockitoBean
    private lateinit var regenerationService: SectionRegenerationService

    @MockitoBean
    private lateinit var editService: SectionEditService

    @MockitoBean
    private lateinit var restoreService: VersionRestoreService

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    private val expId = UUID.randomUUID().toString()
    private val targetId = UUID.randomUUID().toString()
    private val templateId = UUID.randomUUID().toString()
    private val artifactId = UUID.randomUUID().toString()
    private val jobId = UUID.randomUUID().toString()

    private fun jobResponse(kind: ArtifactKind = ArtifactKind.RESUME) = GenerationJobResponse(
        jobId = jobId,
        kind = kind,
        status = GenerationJobStatus.PENDING,
        artifactId = null,
        errorCode = null,
        errorMessage = null,
        targetCompany = "토스",
        createdAt = "2026-06-22T00:00:00Z",
    )

    @Test
    fun 이력서_생성_제출은_202와_jobId를_반환한다() {
        // given (비동기 전환) — 즉시 생성하지 않고 PENDING 작업을 만들어 jobId를 돌려준다.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationJobService.submitResume(any(), any())).thenReturn(jobResponse())
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.jobId") { value(jobId) }
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun 포트폴리오_생성_제출은_202와_jobId를_반환한다() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationJobService.submitPortfolio(any(), any()))
            .thenReturn(jobResponse(ArtifactKind.PORTFOLIO))
        val request = PortfolioGenerationRequest(listOf(expId), targetId)

        // when and then
        mockMvc.post("/artifacts/portfolio") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.kind") { value("PORTFOLIO") }
            jsonPath("$.jobId") { value(jobId) }
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
    fun 이력서_요청에_양식이_없어도_제출되어_202를_반환한다() {
        // given (도메인 이해 §178·§446, 수용 기준 22) — 양식은 선택이다. null이면 400이 아니라 AI 생성 양식 경로.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(generationJobService.submitResume(any(), any())).thenReturn(jobResponse())
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId = null)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.jobId") { value(jobId) }
        }
    }

    @Test
    fun 한도_초과_제출은_429를_반환한다() {
        // given (수용 기준 15) — 제출 시 사전 점검에서 막히면 429.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(QuotaExceededException("오늘 만들 수 있는 횟수를 모두 썼어요.", code = "GENERATION_QUOTA_EXCEEDED", action = "EDIT_MANUALLY"))
            .whenever(generationJobService).submitResume(any(), any())
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("GENERATION_QUOTA_EXCEEDED") }
        }
    }

    @Test
    fun 목표가_없는_제출은_404를_반환한다() {
        // given (소유 격리) — 목표 미존재·타인 모두 404.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        doThrow(ResourceNotFoundException("선택한 목표 정보를 찾을 수 없어요."))
            .whenever(generationJobService).submitResume(any(), any())
        val request = ResumeGenerationRequest(listOf(expId), targetId, templateId)

        // when and then
        mockMvc.post("/artifacts/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun 산출물_목록을_200으로_반환한다() {
        // given — 카드용 요약 목록.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(readService.listArtifacts(any())).thenReturn(
            listOf(
                ArtifactSummaryResponse(
                    id = artifactId,
                    kind = ArtifactKind.RESUME,
                    targetCompany = "토스",
                    createdAt = "2026-06-22T00:00:00Z",
                    updatedAt = "2026-06-22T01:00:00Z",
                ),
            ),
        )

        // when and then
        mockMvc.get("/artifacts")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(artifactId) }
                jsonPath("$[0].targetCompany") { value("토스") }
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

    @Test
    fun 항목_재생성에_성공하면_200과_갱신된_활성_버전을_반환한다() {
        // given (수용 기준 10·19) — 갱신된 산출물(새 활성 버전)을 그대로 내려준다.
        val sectionId = UUID.randomUUID().toString()
        val newVersionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(regenerationService.regenerateSection(any(), any())).thenReturn(
            ArtifactResponse(
                id = artifactId,
                kind = ArtifactKind.RESUME,
                activeVersion = ArtifactVersionResponse(
                    versionId = newVersionId,
                    sections = listOf(
                        ArtifactSectionResponse(
                            id = UUID.randomUUID().toString(),
                            sectionKind = SectionKind.SUMMARY,
                            definitionKey = "section-0-요약",
                            content = "다시 만든 요약",
                            status = SectionStatus.GENERATED,
                            sourceExperienceIds = listOf(expId),
                        ),
                    ),
                ),
            ),
        )
        val request = RegenerateSectionRequest(directive = "더 짧게")

        // when and then
        mockMvc.post("/artifacts/$artifactId/sections/$sectionId/regenerate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.activeVersion.versionId") { value(newVersionId) }
            jsonPath("$.activeVersion.sections[0].content") { value("다시 만든 요약") }
        }
    }

    @Test
    fun 같은_항목_동시_재생성은_409와_진행중_안내를_반환한다() {
        // given (수용 기준 20) — 진행 중이면 ConflictException → 409 + RETRY_LATER 액션.
        val sectionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(regenerationService.regenerateSection(any(), any()))
            .thenThrow(ConflictException("이 항목은 지금 다시 만드는 중이에요. 잠시 후 결과를 확인하거나 다시 시도해 주세요.", action = "RETRY_LATER"))
        val request = RegenerateSectionRequest(directive = null)

        // when and then
        mockMvc.post("/artifacts/$artifactId/sections/$sectionId/regenerate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONFLICT") }
            jsonPath("$.action") { value("RETRY_LATER") }
        }
    }

    @Test
    fun 타인_소유_또는_미존재_항목_재생성은_404를_반환한다() {
        // given (소유 격리, 수용 기준 13) — 미존재·타인 모두 404.
        val sectionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(regenerationService.regenerateSection(any(), any()))
            .thenThrow(ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요."))
        val request = RegenerateSectionRequest(directive = null)

        // when and then
        mockMvc.post("/artifacts/$artifactId/sections/$sectionId/regenerate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun 항목_직접_편집에_성공하면_200과_갱신된_활성_버전을_반환한다() {
        // given (수용 기준 10·19, §267) — 갱신된 산출물(새 활성 버전)을 그대로 내려준다.
        val sectionId = UUID.randomUUID().toString()
        val newVersionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(editService.editSectionContent(any(), any())).thenReturn(
            ArtifactResponse(
                id = artifactId,
                kind = ArtifactKind.RESUME,
                activeVersion = ArtifactVersionResponse(
                    versionId = newVersionId,
                    sections = listOf(
                        ArtifactSectionResponse(
                            id = UUID.randomUUID().toString(),
                            sectionKind = SectionKind.SUMMARY,
                            definitionKey = "section-0-요약",
                            content = "직접 고친 요약",
                            status = SectionStatus.GENERATED,
                            sourceExperienceIds = listOf(expId),
                        ),
                    ),
                ),
            ),
        )
        val request = EditSectionContentRequest(content = "직접 고친 요약")

        // when and then
        mockMvc.put("/artifacts/$artifactId/sections/$sectionId/content") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.activeVersion.versionId") { value(newVersionId) }
            jsonPath("$.activeVersion.sections[0].content") { value("직접 고친 요약") }
        }
    }

    @Test
    fun 직접_편집_요청에_내용이_비어있으면_400을_반환한다() {
        // given (UX 에러 가이드) — 빈 content는 형식 검증으로 거부한다(@NotBlank).
        val sectionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        val request = EditSectionContentRequest(content = "   ")

        // when and then
        mockMvc.put("/artifacts/$artifactId/sections/$sectionId/content") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.field") { value("content") }
        }
    }

    @Test
    fun 타인_소유_또는_미존재_항목_직접_편집은_404를_반환한다() {
        // given (소유 격리, 수용 기준 13) — 미존재·타인(산출물/항목) 모두 404.
        val sectionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(editService.editSectionContent(any(), any()))
            .thenThrow(ResourceNotFoundException("수정할 항목을 찾을 수 없어요."))
        val request = EditSectionContentRequest(content = "고친 내용")

        // when and then
        mockMvc.put("/artifacts/$artifactId/sections/$sectionId/content") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun 버전_목록을_조회하면_200과_모든_버전을_생성순서로_반환한다() {
        // given (수용 기준 11·12, §363) — 두 버전을 생성 순서로, 활성 표시·섹션 데이터와 함께 내려준다.
        val olderVersionId = UUID.randomUUID().toString()
        val newerVersionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(readService.getVersions(any(), any())).thenReturn(
            ArtifactVersionsResponse(
                artifactId = artifactId,
                kind = ArtifactKind.RESUME,
                activeVersionId = newerVersionId,
                versions = listOf(
                    VersionHistoryResponse(
                        versionId = olderVersionId,
                        active = false,
                        createdAt = "2026-06-15T00:00:00Z",
                        sections = listOf(
                            ArtifactSectionResponse(
                                id = UUID.randomUUID().toString(),
                                sectionKind = SectionKind.SUMMARY,
                                definitionKey = "section-0-요약",
                                content = "이전 요약",
                                status = SectionStatus.GENERATED,
                                sourceExperienceIds = listOf(expId),
                            ),
                        ),
                    ),
                    VersionHistoryResponse(
                        versionId = newerVersionId,
                        active = true,
                        createdAt = "2026-06-16T00:00:00Z",
                        sections = listOf(
                            ArtifactSectionResponse(
                                id = UUID.randomUUID().toString(),
                                sectionKind = SectionKind.SUMMARY,
                                definitionKey = "section-0-요약",
                                content = "최신 요약",
                                status = SectionStatus.GENERATED,
                                sourceExperienceIds = listOf(expId),
                            ),
                        ),
                    ),
                ),
            ),
        )

        // when and then
        mockMvc.get("/artifacts/$artifactId/versions")
            .andExpect {
                status { isOk() }
                jsonPath("$.activeVersionId") { value(newerVersionId) }
                jsonPath("$.versions[0].versionId") { value(olderVersionId) }
                jsonPath("$.versions[0].active") { value(false) }
                jsonPath("$.versions[1].active") { value(true) }
                jsonPath("$.versions[1].sections[0].content") { value("최신 요약") }
            }
    }

    @Test
    fun 타인_소유_또는_미존재_산출물_버전_목록조회는_404를_반환한다() {
        // given (소유 격리, 수용 기준 13) — 미존재·타인 모두 404.
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(readService.getVersions(any(), any()))
            .thenThrow(ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요."))

        // when and then
        mockMvc.get("/artifacts/$artifactId/versions")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun 버전_복원에_성공하면_200과_활성이_전환된_산출물을_반환한다() {
        // given (§277·§283, "복원 = 활성 전환") — 고른 버전이 활성으로 바뀐 산출물을 내려준다.
        val versionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(restoreService.restoreVersion(any(), any())).thenReturn(
            ArtifactResponse(
                id = artifactId,
                kind = ArtifactKind.RESUME,
                activeVersion = ArtifactVersionResponse(
                    versionId = versionId,
                    sections = listOf(
                        ArtifactSectionResponse(
                            id = UUID.randomUUID().toString(),
                            sectionKind = SectionKind.SUMMARY,
                            definitionKey = "section-0-요약",
                            content = "복원된 요약",
                            status = SectionStatus.GENERATED,
                            sourceExperienceIds = listOf(expId),
                        ),
                    ),
                ),
            ),
        )

        // when and then
        mockMvc.post("/artifacts/$artifactId/versions/$versionId/restore")
            .andExpect {
                status { isOk() }
                jsonPath("$.activeVersion.versionId") { value(versionId) }
                jsonPath("$.activeVersion.sections[0].content") { value("복원된 요약") }
            }
    }

    @Test
    fun 타인_소유_또는_미존재_버전_복원은_404를_반환한다() {
        // given (소유 격리, 수용 기준 13) — 미존재·타인(산출물/버전) 모두 404.
        val versionId = UUID.randomUUID().toString()
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(restoreService.restoreVersion(any(), any()))
            .thenThrow(ResourceNotFoundException("되돌릴 버전을 찾을 수 없어요."))

        // when and then
        mockMvc.post("/artifacts/$artifactId/versions/$versionId/restore")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }
}
