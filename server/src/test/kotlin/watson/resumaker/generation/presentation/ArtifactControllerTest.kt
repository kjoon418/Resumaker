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
import org.springframework.test.web.servlet.put
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.artifact.domain.SectionStatus
import watson.resumaker.common.domain.ConflictException
import watson.resumaker.common.domain.EmptyExperienceSelectionException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.application.ArtifactGenerationService
import watson.resumaker.generation.application.ArtifactReadService
import watson.resumaker.generation.application.SectionEditService
import watson.resumaker.generation.application.SectionRegenerationService
import watson.resumaker.generation.application.VersionRestoreService
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
    fun 재생성_요청에_개선_지시_없이_빈_본문이어도_200을_반환한다() {
        // given — directive는 선택 필드. 목표는 산출물 스냅샷에서 읽으므로 요청 본문에 필수값 없음(§347).
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
        val request = RegenerateSectionRequest(directive = null)

        // when and then
        mockMvc.post("/artifacts/$artifactId/sections/$sectionId/regenerate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.activeVersion.versionId") { value(newVersionId) }
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
