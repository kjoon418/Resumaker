package watson.resumaker.feature.artifact

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeArtifactApi
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.ArtifactSectionResponse
import watson.resumaker.model.dto.ArtifactVersionResponse
import watson.resumaker.model.dto.GeneratedSectionResponse
import watson.resumaker.model.dto.GenerationResponse
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
        return ArtifactViewModel(api, artifactId = "a-1")
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
        val vm = ArtifactViewModel(api, artifactId = "a-1")
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
        val vm = ArtifactViewModel(api, artifactId = "a-1")
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
        val vm = ArtifactViewModel(api, artifactId = "missing")
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
        val vm = ArtifactViewModel(api, artifactId = "a-1", initial = initial)
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
        val vm = ArtifactViewModel(api, artifactId = "a-1", initial = null)
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
}
