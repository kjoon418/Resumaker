package watson.resumaker.feature.artifact

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeArtifactApi
import watson.resumaker.fake.FakeExperienceApi
import watson.resumaker.fake.FakeTargetApi
import watson.resumaker.fake.FakeTemplateApi
import watson.resumaker.fake.sampleExperience
import watson.resumaker.model.dto.GeneratedSectionResponse
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.SectionCharacter
import watson.resumaker.model.type.SectionKind
import watson.resumaker.model.type.SectionStatus
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactCreateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun target(id: String) = TargetResponse(id = id, recruitDirection = "백엔드 개발자")
    private fun template(id: String) =
        TemplateResponse(id = id, name = "표준 양식", sections = listOf(SectionResponse("요약", SectionCharacter.SUMMARY, false)))

    private fun section(id: String, status: SectionStatus) = GeneratedSectionResponse(
        sectionId = id,
        definitionKey = "summary",
        sectionKind = SectionKind.SUMMARY,
        content = "내용 $id",
        status = status,
        sourceExperienceIds = listOf("e-1"),
        factGroundings = emptyList(),
    )

    private fun vmWith(
        artifactApi: FakeArtifactApi,
        experiences: List<watson.resumaker.model.dto.ExperienceResponse> = listOf(sampleExperience(id = "e-1")),
        targets: List<TargetResponse> = listOf(target("t-1")),
        templates: List<TemplateResponse> = listOf(template("tpl-1")),
    ) = ArtifactCreateViewModel(
        artifactApi = artifactApi,
        experienceApi = FakeExperienceApi(getAllResult = ApiResult.Success(experiences)),
        targetApi = FakeTargetApi(getAllResult = ApiResult.Success(targets)),
        templateApi = FakeTemplateApi(getAllResult = ApiResult.Success(templates)),
    )

    @Test
    fun emptyExperiencesTriggersPreventiveBranch() = runTest(dispatcher) {
        val vm = vmWith(FakeArtifactApi(), experiences = emptyList())
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.hasNoExperiences)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun formValidationBlocksSubmitUntilRequiredSelected() = runTest(dispatcher) {
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        // 아무 것도 선택 안 함 → 생성 불가.
        assertFalse(vm.state.value.canSubmit)

        vm.toggleExperience("e-1")
        assertFalse(vm.state.value.canSubmit) // 목표·양식 미선택.
        vm.selectTarget("t-1")
        assertFalse(vm.state.value.canSubmit) // 이력서는 양식 필수.
        vm.selectTemplate("tpl-1")
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun portfolioDoesNotRequireTemplate() = runTest(dispatcher) {
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        vm.selectKind(ArtifactKind.PORTFOLIO)
        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")

        assertFalse(vm.state.value.templateRequired)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun generateResumeSuccessExposesGeneratedResponse() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Success(
                GenerationResponse(
                    artifactId = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersionId = "v-1",
                    sections = listOf(section("s-1", SectionStatus.GENERATED)),
                ),
            ),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()

        assertEquals("a-1", vm.state.value.generated?.artifactId)
        assertNull(vm.state.value.generationError)
        assertEquals(listOf("e-1"), api.lastResumeRequest?.experienceIds)
        assertEquals("tpl-1", api.lastResumeRequest?.templateId)
    }

    @Test
    fun partialSuccessIsTreatedAsSuccess() = runTest(dispatcher) {
        // 부분 성공(200): 일부 항목 *_FAILED여도 성공 응답으로 받아 열람으로 이동(가짜 성공 금지는 열람에서 고지).
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Success(
                GenerationResponse(
                    artifactId = "a-2",
                    kind = ArtifactKind.RESUME,
                    activeVersionId = "v-2",
                    sections = listOf(
                        section("s-1", SectionStatus.GENERATED),
                        section("s-2", SectionStatus.GENERATION_FAILED),
                    ),
                ),
            ),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.generated)
        assertNull(vm.state.value.generationError)
    }

    @Test
    fun emptyBundle409ExposesErrorCodeAndAction() = runTest(dispatcher) {
        // 서버 GlobalExceptionHandler: 409 EMPTY_EXPERIENCE_SELECTION + action=ADD_EXPERIENCE.
        // code는 "왜 실패했나", action은 "사용자가 할 일 힌트". 둘 다 Failure에 보존해야 한다.
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Failure(
                message = "이력서·포트폴리오를 만들려면 경험을 하나 이상 골라 주세요.",
                code = "EMPTY_EXPERIENCE_SELECTION",
                action = "ADD_EXPERIENCE",
            ),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()

        assertEquals("EMPTY_EXPERIENCE_SELECTION", vm.state.value.generationErrorCode)
        assertEquals("ADD_EXPERIENCE", vm.state.value.generationAction)
        assertNotNull(vm.state.value.generationError)
        assertNull(vm.state.value.generated)
        assertFalse(vm.state.value.generating)
    }
}
