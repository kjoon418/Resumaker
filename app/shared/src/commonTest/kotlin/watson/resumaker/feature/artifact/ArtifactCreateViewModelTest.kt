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
import watson.resumaker.model.dto.GenerationJobResponse
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.GenerationJobStatus
import watson.resumaker.model.type.SectionCharacter
import watson.resumaker.model.type.StrategyStatus
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

    private fun job(id: String, kind: ArtifactKind = ArtifactKind.RESUME) = GenerationJobResponse(
        jobId = id,
        kind = kind,
        status = GenerationJobStatus.PENDING,
        createdAt = "2026-06-22T00:00:00Z",
    )

    private fun vmWith(
        artifactApi: FakeArtifactApi,
        experiences: List<watson.resumaker.model.dto.ExperienceResponse> = listOf(sampleExperience(id = "e-1")),
        targets: List<TargetResponse> = listOf(target("t-1")),
        templates: List<TemplateResponse> = listOf(template("tpl-1")),
        prefillJob: GenerationJobResponse? = null,
    ) = ArtifactCreateViewModel(
        artifactApi = artifactApi,
        experienceApi = FakeExperienceApi(getAllResult = ApiResult.Success(experiences)),
        targetApi = FakeTargetApi(getAllResult = ApiResult.Success(targets)),
        templateApi = FakeTemplateApi(getAllResult = ApiResult.Success(templates)),
        prefillJob = prefillJob,
    )

    /** EDIT_INPUTS 재시도 프리필 작업(실패 작업이 보관한 입력). */
    private fun prefill(
        jobId: String = "job-failed",
        kind: ArtifactKind = ArtifactKind.RESUME,
        experienceIds: List<String> = listOf("e-1"),
        targetId: String = "t-1",
        templateId: String? = "tpl-1",
    ) = GenerationJobResponse(
        jobId = jobId,
        kind = kind,
        status = GenerationJobStatus.FAILED,
        createdAt = "2026-06-22T00:00:00Z",
        experienceIds = experienceIds,
        targetId = targetId,
        templateId = templateId,
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
    fun submitBlockReasonListsMissingRequirementsForResume() = runTest(dispatcher) {
        // UX-04: 만들기 버튼이 비활성일 때 무엇이 부족한지 구체적으로 도출한다(이력서는 경험·목표·양식 필수).
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        // 아무 것도 선택 안 함 → 세 항목 모두 부족.
        assertEquals("경험, 목표, 이력서 양식 선택이 필요해요", vm.state.value.submitBlockReason)

        vm.toggleExperience("e-1")
        assertEquals("목표, 이력서 양식 선택이 필요해요", vm.state.value.submitBlockReason)

        vm.selectTarget("t-1")
        assertEquals("이력서 양식 선택이 필요해요", vm.state.value.submitBlockReason)

        vm.selectTemplate("tpl-1")
        // 모두 충족 → 비활성 사유 없음.
        assertTrue(vm.state.value.canSubmit)
        assertNull(vm.state.value.submitBlockReason)
    }

    @Test
    fun submitBlockReasonExcludesTemplateForPortfolio() = runTest(dispatcher) {
        // 포트폴리오는 양식 단계가 없으므로 양식을 미충족 항목으로 세지 않는다.
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        vm.selectKind(ArtifactKind.PORTFOLIO)
        assertEquals("경험, 목표 선택이 필요해요", vm.state.value.submitBlockReason)

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        assertTrue(vm.state.value.canSubmit)
        assertNull(vm.state.value.submitBlockReason)
    }

    @Test
    fun submitBlockReasonIsNullWhileGenerating() = runTest(dispatcher) {
        // 생성 중에는 비활성 사유 대신 진행 안내를 보여주므로 사유는 null이다.
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate() // generating=true 상태 진입(코루틴 미실행).

        assertTrue(vm.state.value.generating)
        assertNull(vm.state.value.submitBlockReason)
    }

    @Test
    fun selectedTargetStrategyNotReadyReflectsTargetStatus() = runTest(dispatcher) {
        val ready = TargetResponse(id = "t-ready", recruitDirection = "방향", strategyStatus = StrategyStatus.READY)
        val pending = TargetResponse(id = "t-pending", recruitDirection = "방향", strategyStatus = StrategyStatus.PENDING)
        val vm = vmWith(FakeArtifactApi(), targets = listOf(ready, pending))
        testScheduler.advanceUntilIdle()

        // 미선택이면 caption 없음.
        assertFalse(vm.state.value.selectedTargetStrategyNotReady)

        vm.selectTarget("t-ready")
        assertFalse(vm.state.value.selectedTargetStrategyNotReady) // READY → caption 없음.

        vm.selectTarget("t-pending")
        assertTrue(vm.state.value.selectedTargetStrategyNotReady) // 분석 중 → caption 노출.
    }

    @Test
    fun portfolioDoesNotShowTemplateStep() = runTest(dispatcher) {
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        vm.selectKind(ArtifactKind.PORTFOLIO)
        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")

        assertFalse(vm.state.value.templateStepVisible)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun aiTemplateChoiceSatisfiesValidationWithoutConcreteTemplate() = runTest(dispatcher) {
        // 양식 자동(AI에 맡기기)을 고르면 구체 양식 없이도 생성 가능(서버 §178 — 양식은 더 이상 필수 아님).
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        assertFalse(vm.state.value.canSubmit) // 양식 선택(구체/자동) 전.

        vm.selectAiTemplate()
        assertTrue(vm.state.value.useAiTemplate)
        assertNull(vm.state.value.selectedTemplateId)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun aiTemplateAndConcreteTemplateAreExclusive() = runTest(dispatcher) {
        val vm = vmWith(FakeArtifactApi())
        testScheduler.advanceUntilIdle()

        vm.selectAiTemplate()
        assertTrue(vm.state.value.useAiTemplate)

        // 구체 양식을 고르면 자동 선택이 해제된다.
        vm.selectTemplate("tpl-1")
        assertFalse(vm.state.value.useAiTemplate)
        assertEquals("tpl-1", vm.state.value.selectedTemplateId)

        // 다시 자동을 고르면 구체 양식이 비워진다.
        vm.selectAiTemplate()
        assertTrue(vm.state.value.useAiTemplate)
        assertNull(vm.state.value.selectedTemplateId)
    }

    @Test
    fun generateWithAiTemplateSendsNullTemplateId() = runTest(dispatcher) {
        // 양식 자동 생성 시 서버로 templateId=null이 전송된다(AI 생성 양식 경로).
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Success(job("job-3")),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectAiTemplate()
        vm.generate()
        testScheduler.advanceUntilIdle()

        // 제출 성공 → 목록 이동 신호.
        assertTrue(vm.state.value.submitted)
        assertNull(api.lastResumeRequest?.templateId)
        assertEquals(listOf("e-1"), api.lastResumeRequest?.experienceIds)
    }

    @Test
    fun generateResumeSubmitSignalsListNavigation() = runTest(dispatcher) {
        // 비동기 전환: 제출(202) 성공 시 산출물을 즉시 받지 않고 submitted 신호로 목록으로 이동한다.
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Success(job("job-1")),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.submitted)
        assertNull(vm.state.value.generationError)
        assertEquals(listOf("e-1"), api.lastResumeRequest?.experienceIds)
        assertEquals("tpl-1", api.lastResumeRequest?.templateId)
    }

    @Test
    fun consumeSubmittedClearsSignal() = runTest(dispatcher) {
        // 화면이 1회 소비하면 submitted가 내려가 중복 내비를 막는다.
        val api = FakeArtifactApi(generateResumeResult = ApiResult.Success(job("job-2")))
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.submitted)

        vm.consumeSubmitted()
        assertFalse(vm.state.value.submitted)
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
        assertFalse(vm.state.value.submitted)
        assertFalse(vm.state.value.generating)
    }

    @Test
    fun generationQuotaExceeded429SetsQuotaFlag() = runTest(dispatcher) {
        // 서버: 1차 생성 일일 한도 초과 → 429 GENERATION_QUOTA_EXCEEDED. 배너 톤을 한도초과용으로 분기한다.
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Failure(
                message = "오늘 만들 수 있는 횟수를 다 썼어요. 내일 다시 시도하거나 기존 산출물을 다듬어 보세요.",
                code = "GENERATION_QUOTA_EXCEEDED",
            ),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()

        assertEquals("GENERATION_QUOTA_EXCEEDED", vm.state.value.generationErrorCode)
        assertEquals(true, vm.state.value.isGenerationQuotaExceeded)
        assertNotNull(vm.state.value.generationError)
        assertFalse(vm.state.value.submitted)
    }

    @Test
    fun retryGenerateReissuesApiCallAfterFailure() = runTest(dispatcher) {
        // #4: 생성 실패 후 retryGenerate()는 오류를 닫는 데 그치지 않고 API를 실제로 재호출한다.
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Failure(
                message = "AI 생성 서비스를 일시적으로 사용할 수 없어요. 잠시 후 다시 시도해 주세요.",
                code = "AI_GENERATION_UNAVAILABLE",
            ),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()

        // 첫 시도 실패 확인.
        assertNotNull(vm.state.value.generationError)
        assertEquals(1, api.generateResumeCallCount)

        // 재시도: 이번엔 제출 성공으로 교체.
        api.generateResumeResult = ApiResult.Success(job("job-retry"))
        vm.retryGenerate()
        testScheduler.advanceUntilIdle()

        // API가 실제로 재호출됐고 제출 성공 신호가 반영된다.
        assertEquals(2, api.generateResumeCallCount)
        assertNull(vm.state.value.generationError)
        assertTrue(vm.state.value.submitted)
    }

    @Test
    fun prefillJobRestoresSelectionsAndIsSubmittable() = runTest(dispatcher) {
        // EDIT_INPUTS 재시도: 실패 작업의 입력(경험·목표·양식)이 그대로 채워져 바로 다시 만들 수 있다.
        val vm = vmWith(FakeArtifactApi(), prefillJob = prefill())
        testScheduler.advanceUntilIdle()

        assertEquals(ArtifactKind.RESUME, vm.state.value.kind)
        assertEquals(setOf("e-1"), vm.state.value.selectedExperienceIds)
        assertEquals("t-1", vm.state.value.selectedTargetId)
        assertEquals("tpl-1", vm.state.value.selectedTemplateId)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun prefillWithNullTemplateRestoresAiTemplateChoice() = runTest(dispatcher) {
        // 이력서이면서 양식 미지정(null)이었던 실패 작업은 'AI 양식 자동' 선택으로 복원된다(원래 AI 생성 양식 경로).
        val vm = vmWith(FakeArtifactApi(), prefillJob = prefill(templateId = null))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.useAiTemplate)
        assertNull(vm.state.value.selectedTemplateId)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun prefillPrunesSelectionsThatNoLongerExist() = runTest(dispatcher) {
        // SOURCE_MISSING처럼 경험·목표가 삭제된 뒤 프리필되면, 존재하지 않는 선택을 솎아내 같은 실패를 반복하지 않는다.
        val vm = vmWith(
            FakeArtifactApi(),
            experiences = listOf(sampleExperience(id = "e-keep")),
            prefillJob = prefill(experienceIds = listOf("e-gone"), targetId = "t-gone", templateId = null),
        )
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.selectedExperienceIds.isEmpty()) // e-gone 솎아냄
        assertNull(vm.state.value.selectedTargetId)                // t-gone 솎아냄
        assertFalse(vm.state.value.canSubmit)                      // 다시 골라야 제출 가능
    }

    @Test
    fun prefillDeletesSourceFailedJobAfterSuccessfulSubmit() = runTest(dispatcher) {
        // 새 작업을 성공 제출하면 원본 실패 작업을 삭제해 잔존 실패 기록을 정리한다.
        val api = FakeArtifactApi(generateResumeResult = ApiResult.Success(job("job-new")))
        val vm = vmWith(api, prefillJob = prefill(jobId = "job-failed"))
        testScheduler.advanceUntilIdle()

        vm.generate()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.submitted)
        assertEquals("job-failed", api.deletedJobId)
    }

    @Test
    fun retryGenerateClearsErrorBeforeReissuing() = runTest(dispatcher) {
        // #4: retryGenerate()는 API 재호출 전에 이전 오류 상태를 먼저 지운다(UI가 중간에 오류+로딩을 동시에 보이지 않음).
        val api = FakeArtifactApi(
            generateResumeResult = ApiResult.Failure(
                message = "서버 오류가 발생했어요.",
                code = "INTERNAL_ERROR",
            ),
        )
        val vm = vmWith(api)
        testScheduler.advanceUntilIdle()

        vm.toggleExperience("e-1")
        vm.selectTarget("t-1")
        vm.selectTemplate("tpl-1")
        vm.generate()
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.generationError)

        // retryGenerate 직후(코루틴 실행 전) 오류가 지워져야 한다.
        vm.retryGenerate()
        assertNull(vm.state.value.generationError)
    }
}
