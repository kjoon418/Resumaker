package watson.resumaker.feature.artifact

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceTimeBy
import watson.resumaker.fake.FakeArtifactApi
import watson.resumaker.fake.FakeQualityApi
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.ArtifactSectionResponse
import watson.resumaker.model.dto.ArtifactVersionResponse
import watson.resumaker.model.dto.CandidateDto
import watson.resumaker.model.dto.GeneratedSectionResponse
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.dto.QualityImprovementJobResponse
import watson.resumaker.model.dto.QualityJobStatus
import watson.resumaker.model.type.ArtifactKind
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
class ArtifactViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewSection(id: String, status: SectionStatus, content: String = "내용 $id") =
        ArtifactSectionResponse(
            id = id,
            sectionKind = SectionKind.CAREER,
            definitionKey = "career",
            content = content,
            status = status,
            sourceExperienceIds = listOf("e-1"),
        )

    private fun artifact(
        section: ArtifactSectionResponse,
        prunedVersionCount: Int = 0,
        versionId: String = "v-2",
    ) = ArtifactResponse(
        id = "a-1",
        kind = ArtifactKind.RESUME,
        activeVersion = ArtifactVersionResponse(versionId = versionId, sections = listOf(section)),
        prunedVersionCount = prunedVersionCount,
    )

    /** 단일 GENERATED 항목 산출물을 적재한 ViewModel을 만든다(액션 테스트의 출발점). */
    private fun loadedViewModel(
        api: FakeArtifactApi,
        sectionId: String = "s-1",
        content: String = "원본 내용",
    ): ArtifactViewModel {
        api.getArtifactResult = ApiResult.Success(
            ArtifactResponse(
                id = "a-1",
                kind = ArtifactKind.RESUME,
                activeVersion = ArtifactVersionResponse(
                    versionId = "v-1",
                    sections = listOf(viewSection(sectionId, SectionStatus.GENERATED, content)),
                ),
            ),
        )
        return ArtifactViewModel(api, qualityApi = FakeQualityApi(), artifactId = "a-1")
    }

    @Test
    fun loadSuccessMapsSections() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Success(
                ArtifactResponse(
                    id = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersion = ArtifactVersionResponse(
                        versionId = "v-1",
                        sections = listOf(viewSection("s-1", SectionStatus.GENERATED)),
                    ),
                ),
            ),
        )
        val vm = ArtifactViewModel(api, qualityApi = FakeQualityApi(), artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(1, vm.state.value.sections.size)
        assertEquals("a-1", api.getArtifactId)
        assertFalse(vm.state.value.hasFailedSections)
    }

    @Test
    fun failedSectionsAreFlaggedAndExcludedFromCopy() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Success(
                ArtifactResponse(
                    id = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersion = ArtifactVersionResponse(
                        versionId = "v-1",
                        sections = listOf(
                            viewSection("ok", SectionStatus.GENERATED, content = "정상 내용"),
                            viewSection("bad", SectionStatus.GENERATION_FAILED, content = "쓰레기"),
                        ),
                    ),
                ),
            ),
        )
        val vm = ArtifactViewModel(api, qualityApi = FakeQualityApi(), artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.hasFailedSections)
        // 전체 복사 텍스트는 정상 항목만 포함(가짜 성공 금지).
        assertEquals("정상 내용", vm.state.value.fullCopyText)
        assertTrue(vm.state.value.hasCopyableContent)
    }

    @Test
    fun notFoundSetsErrorMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Failure(message = "산출물을 찾을 수 없어요.", code = "404"),
        )
        val vm = ArtifactViewModel(api, qualityApi = FakeQualityApi(), artifactId = "missing")
        testScheduler.advanceUntilIdle()

        assertEquals("산출물을 찾을 수 없어요.", vm.state.value.errorMessage)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun initialSeedsImmediatelyThenLoadFetchesServerState() = runTest(dispatcher) {
        // load-after-initial: initial로 즉시 시드하고, load()가 서버 최신 상태로 덮어쓴다.
        // 이 방식으로 버전 화면 push/pop 후 VM이 재생성돼도 복원·재생성 결과가 반영된다(§287).
        val serverSection = viewSection("s-1", SectionStatus.GENERATED, content = "서버 최신 내용")
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Success(
                ArtifactResponse(
                    id = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersion = ArtifactVersionResponse(
                        versionId = "v-2",
                        sections = listOf(serverSection),
                    ),
                ),
            ),
        )
        val initial = GenerationResponse(
            artifactId = "a-1",
            kind = ArtifactKind.PORTFOLIO,
            activeVersionId = "v-1",
            sections = listOf(
                GeneratedSectionResponse(
                    sectionId = "s-1",
                    definitionKey = "narrative",
                    sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
                    content = "초기 생성 내용",
                    status = SectionStatus.GENERATED,
                    sourceExperienceIds = listOf("e-1"),
                    factGroundings = emptyList(),
                ),
            ),
        )
        val vm = ArtifactViewModel(api, qualityApi = FakeQualityApi(), artifactId = "a-1", initial = initial)
        testScheduler.advanceUntilIdle()

        // getArtifact가 호출돼 서버 상태가 반영된다(load-after-initial).
        assertEquals("a-1", api.getArtifactId)
        assertFalse(vm.state.value.loading)
        // 서버 응답으로 덮어써져 initial 내용이 아닌 서버 최신 내용이 표시된다.
        assertEquals("서버 최신 내용", vm.state.value.sections.single().content)
        // kind도 서버 응답(RESUME)으로 갱신된다.
        assertEquals(ArtifactKind.RESUME, vm.state.value.kind)
    }

    @Test
    fun vmRecreationAfterRestoreAlwaysLoadsServerState() = runTest(dispatcher) {
        // 버전 화면 push/pop 시 VM이 재생성(remember(artifactId) 키 유지이나 컴포지션 이탈→재진입)된다.
        // 복원 후 새 VM은 initial=null로 생성돼 load()를 거쳐 갱신된 활성 버전(복원 결과)을 가져온다(§287).
        val restoredSection = viewSection("s-1", SectionStatus.GENERATED, content = "복원된 v1 내용")
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Success(
                ArtifactResponse(
                    id = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersion = ArtifactVersionResponse(
                        versionId = "v-1",  // 복원으로 v1이 활성
                        sections = listOf(restoredSection),
                    ),
                ),
            ),
        )
        // initial=null: 딥링크·pop 복귀처럼 생성 응답 없이 VM이 새로 만들어지는 상황.
        val vm = ArtifactViewModel(api, qualityApi = FakeQualityApi(), artifactId = "a-1", initial = null)
        testScheduler.advanceUntilIdle()

        assertEquals("a-1", api.getArtifactId)
        assertEquals("복원된 v1 내용", vm.state.value.sections.single().content)
        assertFalse(vm.state.value.loading)
    }

    // ── Slice 2: 항목 재생성 ──────────────────────────────────────────────

    @Test
    fun regenerateSuccessReplacesSection() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.regenerateResult = ApiResult.Success(
            artifact(viewSection("s-1", SectionStatus.GENERATED, content = "다시 만든 내용")),
        )
        vm.regenerateSection("s-1", directive = "더 짧게")
        testScheduler.advanceUntilIdle()

        // 응답으로 항목이 교체되고 in-flight가 해제된다.
        assertEquals("다시 만든 내용", vm.state.value.sections.single().content)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
        // directive는 trim 후 그대로 전달된다.
        assertEquals(Triple("a-1", "s-1", "더 짧게"), api.lastRegenerate)
    }

    @Test
    fun regenerateTrimsBlankDirectiveToNull() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.regenerateResult = ApiResult.Success(artifact(viewSection("s-1", SectionStatus.GENERATED)))
        vm.regenerateSection("s-1", directive = "   ")
        testScheduler.advanceUntilIdle()

        // 공백만인 지시는 "지시 없음"(null)으로 보낸다(빈 지시 허용 — §364).
        assertEquals(null, api.lastRegenerate?.third)
    }

    @Test
    fun regenerateConflictShowsInProgressMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 서버는 동시 재생성을 409(CONFLICT)로 거절한다.
        api.regenerateResult = ApiResult.Failure(
            message = "이 항목은 지금 다시 만드는 중이에요. 잠시 후 결과를 확인하거나 다시 시도해 주세요.",
            code = "CONFLICT",
            action = "RETRY_LATER",
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        // 막다른 길이 아니라 진행 중 안내를 일회성 메시지로 띄우고, 원본 내용은 유지된다.
        assertEquals(
            "이 항목은 지금 다시 만드는 중이에요. 잠시 후 결과를 확인하거나 다시 시도해 주세요.",
            vm.state.value.actionMessage,
        )
        assertEquals("원본 내용", vm.state.value.sections.single().content)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun regeneratePartialFailureKeepsFailedStatus() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 부분 성공: 재생성 항목이 검증 실패여도 서버는 200으로 내려준다(가짜 성공 금지 — 상태로 고지).
        api.regenerateResult = ApiResult.Success(
            artifact(viewSection("s-1", SectionStatus.VALIDATION_FAILED, content = "검증 실패 내용")),
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        assertEquals(SectionStatus.VALIDATION_FAILED, vm.state.value.sections.single().status)
        assertTrue(vm.state.value.hasFailedSections)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun regenerateNotFoundShowsMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.regenerateResult = ApiResult.Failure(message = "다시 만들 항목을 찾을 수 없어요.", code = "NOT_FOUND")
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        assertEquals("다시 만들 항목을 찾을 수 없어요.", vm.state.value.actionMessage)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun regenerateInFlightGuardBlocksDuplicate() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 첫 호출을 게이트로 진행 중에 묶어 둔다.
        val gate = CompletableDeferred<Unit>()
        api.regenerateGate = gate
        api.regenerateResult = ApiResult.Success(artifact(viewSection("s-1", SectionStatus.GENERATED)))

        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isSectionInFlight("s-1"))

        // 진행 중 같은 항목 재생성/편집은 무시된다(중복 호출 차단).
        vm.regenerateSection("s-1", directive = null)
        vm.editSection("s-1", "다른 내용")
        testScheduler.advanceUntilIdle()
        assertEquals(1, api.regenerateCount)
        assertEquals(0, api.editCount)

        // 게이트 해제 후 정상 완료.
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun regeneratePrunedVersionsAreAnnounced() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.regenerateResult = ApiResult.Success(
            artifact(viewSection("s-1", SectionStatus.GENERATED), prunedVersionCount = 2),
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        assertEquals("오래된 버전 2개가 정리됐어요.", vm.state.value.actionMessage)
    }

    @Test
    fun regenerateNoPruneShowsNoAnnouncement() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.regenerateResult = ApiResult.Success(
            artifact(viewSection("s-1", SectionStatus.GENERATED), prunedVersionCount = 0),
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        // 정리가 없으면(0) 고지하지 않는다.
        assertEquals(null, vm.state.value.actionMessage)
    }

    // ── Slice 2: 항목 직접 편집 ───────────────────────────────────────────

    @Test
    fun editSuccessReflectsContent() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 직접 편집은 자동 검증 미적용(§428) — 응답은 항상 사용자 내용을 GENERATED류로 반영한다.
        api.editResult = ApiResult.Success(
            artifact(viewSection("s-1", SectionStatus.GENERATED, content = "직접 고친 내용")),
        )
        vm.editSection("s-1", "직접 고친 내용")
        testScheduler.advanceUntilIdle()

        assertEquals("직접 고친 내용", vm.state.value.sections.single().content)
        assertFalse(vm.state.value.sections.single().failed)
        assertEquals(Triple("a-1", "s-1", "직접 고친 내용"), api.lastEdit)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun editBlankContentDoesNotCallApi() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 빈 내용은 서버 400 전에 클라이언트가 막는다(인라인 검증 — 불필요한 왕복 금지).
        vm.editSection("s-1", "   ")
        testScheduler.advanceUntilIdle()

        assertEquals(0, api.editCount)
        assertEquals(null, api.lastEdit)
    }

    @Test
    fun editNotFoundShowsMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.editResult = ApiResult.Failure(message = "수정할 항목을 찾을 수 없어요.", code = "NOT_FOUND")
        vm.editSection("s-1", "내용")
        testScheduler.advanceUntilIdle()

        assertEquals("수정할 항목을 찾을 수 없어요.", vm.state.value.actionMessage)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun editInFlightGuardBlocksRegenerate() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 편집을 게이트로 붙들어 in-flight 상태를 유지한다.
        val gate = CompletableDeferred<Unit>()
        api.editGate = gate
        api.editResult = ApiResult.Success(artifact(viewSection("s-1", SectionStatus.GENERATED)))

        vm.editSection("s-1", "직접 고친 내용")
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isSectionInFlight("s-1"))

        // 편집 진행 중 같은 항목 재생성 시도 → in-flight 가드가 차단해 API 호출 없어야 한다.
        api.regenerateResult = ApiResult.Success(artifact(viewSection("s-1", SectionStatus.GENERATED)))
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()
        assertEquals(0, api.regenerateCount)

        // 게이트 해제 후 편집 완료, in-flight 소멸.
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    // ── 후속: 재생성 한도 초과(429) → 직접 편집 유도 ──────────────────────────

    @Test
    fun regenerateQuotaExceededPromptsDirectEdit() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 서버는 항목 재생성 한도 초과를 429(REGENERATION_QUOTA_EXCEEDED, action=EDIT_MANUALLY)로 거절한다.
        api.regenerateResult = ApiResult.Failure(
            message = "이 항목은 오늘 다시 만들 수 있는 횟수를 다 썼어요. 내일 이어가거나 직접 편집해 보세요.",
            code = ArtifactViewModel.REGENERATION_QUOTA_EXCEEDED,
            action = ArtifactViewModel.EDIT_MANUALLY_ACTION,
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        // 안내를 일회성 메시지로 띄우고, 직접 편집기를 자동으로 열도록 해당 항목을 가리킨다(막다른 길 금지 — §399).
        assertNotNull(vm.state.value.actionMessage)
        assertEquals("s-1", vm.state.value.editPromptSectionId)
        assertEquals("원본 내용", vm.state.value.sections.single().content)
        assertFalse(vm.state.value.isSectionInFlight("s-1"))
    }

    @Test
    fun regenerateNonEditActionDoesNotPromptEdit() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        // 한도 초과가 아닌 실패(409 동시 재생성 등)는 편집기를 자동으로 열지 않는다.
        api.regenerateResult = ApiResult.Failure(
            message = "이 항목은 지금 다시 만드는 중이에요.",
            code = "CONFLICT",
            action = "RETRY_LATER",
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()

        assertEquals(null, vm.state.value.editPromptSectionId)
    }

    @Test
    fun consumeEditPromptClearsIt() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.regenerateResult = ApiResult.Failure(
            message = "한도 초과",
            code = ArtifactViewModel.REGENERATION_QUOTA_EXCEEDED,
            action = ArtifactViewModel.EDIT_MANUALLY_ACTION,
        )
        vm.regenerateSection("s-1", directive = null)
        testScheduler.advanceUntilIdle()
        assertEquals("s-1", vm.state.value.editPromptSectionId)

        vm.consumeEditPrompt()
        assertEquals(null, vm.state.value.editPromptSectionId)
    }

    @Test
    fun consumeActionMessageClearsIt() = runTest(dispatcher) {
        val api = FakeArtifactApi()
        val vm = loadedViewModel(api)
        testScheduler.advanceUntilIdle()

        api.editResult = ApiResult.Failure(message = "수정할 항목을 찾을 수 없어요.", code = "NOT_FOUND")
        vm.editSection("s-1", "내용")
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.actionMessage)

        vm.consumeActionMessage()
        assertEquals(null, vm.state.value.actionMessage)
    }

    // ── 비차단 품질 개선 진행 카드(§3) ─────────────────────────────────────────

    private fun runningJob(jobId: String = "j-1") = QualityImprovementJobResponse(
        jobId = jobId,
        status = QualityJobStatus.RUNNING,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun succeededJob(candidateCount: Int, jobId: String = "j-1") = QualityImprovementJobResponse(
        jobId = jobId,
        status = QualityJobStatus.SUCCEEDED,
        candidates = (1..candidateCount).map {
            CandidateDto(
                candidateId = "c-$it",
                sectionId = "s-$it",
                definitionKey = "career",
                originalContent = "원본",
                candidateContent = "개선",
                appliedCriterionIds = listOf("crit"),
            )
        },
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun refreshQualityJobImprovingThenReadyByPolling() = runTest(dispatcher) {
        // 화면 진입 시 최신 작업이 진행 중 → 진행 카드. 폴링이 완료를 잡으면 "확인하기" 카드(개선안 수)로 전환된다.
        val api = FakeArtifactApi()
        val quality = FakeQualityApi(latestResult = ApiResult.Success(runningJob("j-9")))
        quality.getJobSequence.add(ApiResult.Success(succeededJob(candidateCount = 2, jobId = "j-9")))
        val vm = loadedQualityViewModel(api, quality)
        testScheduler.advanceUntilIdle()

        // runCurrent: 최신 조회만 실행해 진행 카드를 세우고, 폴링의 delay는 아직 흘리지 않는다(IMPROVING 관찰).
        vm.refreshQualityJob()
        testScheduler.runCurrent()
        assertEquals(QualityImprovementCardUi.Phase.IMPROVING, vm.state.value.qualityImprovement?.phase)

        // 폴링 간격을 흘리면 SUCCEEDED를 잡아 "확인하기"(READY) 카드로 전환된다.
        advanceTimeBy(ArtifactViewModel.QUALITY_POLL_INTERVAL_MS + 100)
        testScheduler.runCurrent()
        val card = vm.state.value.qualityImprovement
        assertEquals(QualityImprovementCardUi.Phase.READY, card?.phase)
        assertEquals(2, card?.candidateCount)
    }

    @Test
    fun refreshQualityJobNoJobLeavesNoCard() = runTest(dispatcher) {
        // 최신 작업이 없으면(204 → null) 카드를 띄우지 않는다.
        val api = FakeArtifactApi()
        val quality = FakeQualityApi(latestResult = ApiResult.Success(null))
        val vm = loadedQualityViewModel(api, quality)
        testScheduler.advanceUntilIdle()

        vm.refreshQualityJob()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.qualityImprovement)
    }

    @Test
    fun dismissQualityJobClearsCardAndCallsServer() = runTest(dispatcher) {
        // "닫기": 카드를 즉시 비우고 서버에 삭제를 요청한다(실패·미채택 작업 치우기).
        val api = FakeArtifactApi()
        val quality = FakeQualityApi(latestResult = ApiResult.Success(succeededJob(candidateCount = 1, jobId = "j-7")))
        val vm = loadedQualityViewModel(api, quality)
        testScheduler.advanceUntilIdle()
        vm.refreshQualityJob()
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.qualityImprovement)

        vm.dismissQualityJob()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.qualityImprovement)
        assertEquals("j-7", quality.dismissedJobId)
    }

    /** 단일 GENERATED 항목 산출물을 적재한, qualityApi를 끼운 ViewModel(진행 카드 테스트 출발점). */
    private fun loadedQualityViewModel(api: FakeArtifactApi, quality: FakeQualityApi): ArtifactViewModel {
        api.getArtifactResult = ApiResult.Success(
            ArtifactResponse(
                id = "a-1",
                kind = ArtifactKind.RESUME,
                activeVersion = ArtifactVersionResponse(
                    versionId = "v-1",
                    sections = listOf(viewSection("s-1", SectionStatus.GENERATED, "원본 내용")),
                ),
            ),
        )
        return ArtifactViewModel(api, qualityApi = quality, artifactId = "a-1")
    }
}
