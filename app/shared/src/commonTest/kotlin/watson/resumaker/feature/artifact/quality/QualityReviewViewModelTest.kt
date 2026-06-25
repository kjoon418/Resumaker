package watson.resumaker.feature.artifact.quality

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeQualityApi
import watson.resumaker.fake.sampleExperience
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.ArtifactSectionResponse
import watson.resumaker.model.dto.ArtifactVersionResponse
import watson.resumaker.model.dto.CandidateDto
import watson.resumaker.model.dto.FindingDto
import watson.resumaker.model.dto.QualityImprovementJobResponse
import watson.resumaker.model.dto.QualityJobStatus
import watson.resumaker.model.dto.QualityReviewResponse
import watson.resumaker.model.dto.ReviewedSectionDto
import watson.resumaker.model.dto.SuggestionGuideDto
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.SectionKind
import watson.resumaker.model.type.SectionStatus
import watson.resumaker.model.type.TreatmentKind
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
class QualityReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun vm(api: FakeQualityApi = FakeQualityApi()) =
        QualityReviewViewModel(qualityApi = api, artifactId = "a-1", artifactKind = ArtifactKind.RESUME)

    private fun autoFinding(
        id: String = "f-1",
        label: String = "강한 동사로 바꾸면 좋아요",
        sectionId: String = "s-1",
    ) = FindingDto(
        findingId = id,
        sectionId = sectionId,
        definitionKey = "career",
        criterionId = "c-1",
        criterionLabel = label,
        treatmentKind = TreatmentKind.AUTO_REWRITE,
        evidenceText = "현재 동사가 약해요",
    )

    private fun suggestionFinding(id: String = "f-2") = FindingDto(
        findingId = id,
        sectionId = "s-2",
        definitionKey = "summary",
        criterionId = "c-2",
        criterionLabel = "구체적인 수치를 추가하면 좋아요",
        treatmentKind = TreatmentKind.SUGGESTION,
        suggestionGuide = SuggestionGuideDto(
            message = "이 경험에 수치를 추가해 보세요.",
            targetExperienceId = "e-1",
        ),
    )

    private fun outOfScopeFinding(id: String = "f-3") = FindingDto(
        findingId = id,
        sectionId = "s-3",
        definitionKey = "format",
        criterionId = "c-3",
        criterionLabel = "서식 기준",
        treatmentKind = TreatmentKind.OUT_OF_SCOPE,
    )

    private fun reviewResponse(vararg findings: FindingDto) = QualityReviewResponse(
        artifactId = "a-1",
        versionId = "v-1",
        findings = findings.toList(),
        // 소견의 항목(sectionId/definitionKey)에서 표시 맥락을 파생한다(서버가 소견 달린 항목만 내려주는 것과 동형).
        sections = findings.map { it.sectionId to it.definitionKey }.distinct()
            .map { (sid, key) -> ReviewedSectionDto(sectionId = sid, definitionKey = key, content = "내용-$sid") },
        autoRewriteCount = findings.count { it.treatmentKind == TreatmentKind.AUTO_REWRITE },
    )

    private fun pendingJob(jobId: String = "j-1") = QualityImprovementJobResponse(
        jobId = jobId,
        status = QualityJobStatus.PENDING,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun succeededJob(vararg candidates: CandidateDto, jobId: String = "j-1") =
        QualityImprovementJobResponse(
            jobId = jobId,
            status = QualityJobStatus.SUCCEEDED,
            candidates = candidates.toList(),
            createdAt = "2026-01-01T00:00:00Z",
        )

    private fun sampleCandidate(id: String = "c-1") = CandidateDto(
        candidateId = id,
        sectionId = "s-1",
        definitionKey = "career",
        originalContent = "원본",
        candidateContent = "개선",
        appliedCriterionIds = listOf("crit-1"),
    )

    private fun sampleArtifact() = ArtifactResponse(
        id = "a-1",
        kind = ArtifactKind.RESUME,
        activeVersion = ArtifactVersionResponse(
            versionId = "v-2",
            sections = listOf(
                ArtifactSectionResponse(
                    id = "s-1",
                    sectionKind = SectionKind.CAREER,
                    definitionKey = "career",
                    content = "개선된 내용",
                    status = SectionStatus.GENERATED,
                    sourceExperienceIds = emptyList(),
                ),
            ),
        ),
        prunedVersionCount = 0,
    )

    // ── 진단 ─────────────────────────────────────────────────────────────────

    @Test
    fun `startReview 성공 - AUTO_REWRITE 소견이 기본 선택된다`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Success(reviewResponse(autoFinding())))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.FINDINGS, vm.state.value.step)
        assertEquals(1, vm.state.value.findings.size)
        assertTrue(vm.state.value.selectedFindingIds.contains("f-1"))
    }

    @Test
    fun `startReview 성공 - SUGGESTION 소견은 선택 목록에 포함되지 않는다`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Success(reviewResponse(suggestionFinding())))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.FINDINGS, vm.state.value.step)
        assertTrue(vm.state.value.selectedFindingIds.isEmpty())
        assertEquals(1, vm.state.value.suggestions.size)
    }

    @Test
    fun `startReview 성공 - OUT_OF_SCOPE 소견은 화면에 노출되지 않는다`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding(), outOfScopeFinding())),
        )
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        // OUT_OF_SCOPE는 필터링되어 findings에 포함되지 않는다.
        assertEquals(1, vm.state.value.findings.size)
        assertEquals(TreatmentKind.AUTO_REWRITE, vm.state.value.findings[0].treatmentKind)
    }

    @Test
    fun `startReview 성공 - 소견 0건이면 빈 상태(긍정 프레이밍)`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Success(reviewResponse()))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.FINDINGS, vm.state.value.step)
        assertTrue(vm.state.value.hasNoFindings)
    }

    @Test
    fun `startReview 실패 - 에러 메시지 세팅 후 IDLE로 복귀`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Failure("서버 오류"))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.IDLE, vm.state.value.step)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun `startReview 진행 중 중복 호출은 무시된다`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Success(reviewResponse(autoFinding())))
        val vm = vm(api)
        vm.startReview()
        vm.startReview() // 두 번째 호출 — 무시돼야 함.
        testScheduler.advanceUntilIdle()

        assertEquals(1, api.reviewCount)
    }

    @Test
    fun `startReview 성공 - 소견을 항목별로 묶어 정박한다`() = runTest(dispatcher) {
        // 같은 항목(s-1)의 소견 2개 + 다른 항목(s-2)의 보강 소견 1개 → 항목 2개로 묶이고 내용·이름이 정박된다.
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(
                reviewResponse(
                    autoFinding("f-1", sectionId = "s-1"),
                    autoFinding("f-2", "또 다른 기준", sectionId = "s-1"),
                    suggestionFinding("f-3"), // sectionId = "s-2"
                ),
            ),
        )
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        val groups = vm.state.value.sectionFindings
        assertEquals(2, groups.size)
        val s1 = groups.first { it.section.sectionId == "s-1" }
        assertEquals("내용-s-1", s1.section.content)
        assertEquals(2, s1.autoRewriteFindings.size) // 같은 항목의 두 소견이 한 카드 아래로 묶인다(중복 오해 해소)
        assertTrue(s1.suggestionFindings.isEmpty())
        val s2 = groups.first { it.section.sectionId == "s-2" }
        assertEquals(1, s2.suggestionFindings.size)
        assertTrue(s2.autoRewriteFindings.isEmpty())
    }

    // ── 소견 선택 토글 ────────────────────────────────────────────────────────

    @Test
    fun `toggleFinding - 선택 해제 후 재선택이 올바르게 동작한다`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Success(reviewResponse(autoFinding("f-1"), autoFinding("f-2", "다른 기준"))))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        // 초기: 둘 다 선택됨.
        assertTrue(vm.state.value.selectedFindingIds.containsAll(setOf("f-1", "f-2")))

        // f-1 해제.
        vm.toggleFinding("f-1")
        assertFalse("f-1" in vm.state.value.selectedFindingIds)
        assertTrue("f-2" in vm.state.value.selectedFindingIds)

        // f-1 재선택.
        vm.toggleFinding("f-1")
        assertTrue("f-1" in vm.state.value.selectedFindingIds)
    }

    @Test
    fun `canSubmitImprovement - 선택이 없으면 false`() = runTest(dispatcher) {
        val api = FakeQualityApi(reviewResult = ApiResult.Success(reviewResponse(autoFinding())))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        // 모든 선택 해제.
        vm.toggleFinding("f-1")
        assertFalse(vm.state.value.canSubmitImprovement)
    }

    // ── 처치 접수 ─────────────────────────────────────────────────────────────

    @Test
    fun `submitImprovement 성공 - IMPROVING 단계로 전환하고 폴링을 시작한다`() = runTest(dispatcher) {
        val pendingJobResponse = pendingJob()
        val succeededJobResponse = succeededJob(sampleCandidate())
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Success(pendingJobResponse),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJobResponse))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        vm.submitImprovement()
        testScheduler.advanceUntilIdle()

        // 폴링 간격 경과 후 SUCCEEDED → CANDIDATES 단계.
        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.CANDIDATES, vm.state.value.step)
        assertEquals(1, vm.state.value.candidates.size)
        assertEquals("c-1", vm.state.value.candidates[0].candidateId)
    }

    @Test
    fun `submitImprovement 429 - 스낵바로 한도 초과 안내, FINDINGS로 복귀`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Failure(
                message = "한도 초과",
                code = QualityReviewViewModel.QUOTA_EXCEEDED_CODE,
            ),
        )
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        vm.submitImprovement()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.FINDINGS, vm.state.value.step)
        assertNotNull(vm.state.value.snackbarMessage)
        assertTrue(vm.state.value.snackbarMessage!!.contains("오늘"))
    }

    // ── 폴링 ─────────────────────────────────────────────────────────────────

    @Test
    fun `폴링 - FAILED 상태이면 FINDINGS로 복귀하고 스낵바를 띄운다`() = runTest(dispatcher) {
        val failedJob = QualityImprovementJobResponse(
            jobId = "j-1",
            status = QualityJobStatus.FAILED,
            errorMessage = "AI 생성 실패",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Success(pendingJob()),
            getJobResult = ApiResult.Success(failedJob),
        )
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        vm.submitImprovement()
        testScheduler.advanceUntilIdle()

        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.FINDINGS, vm.state.value.step)
        assertNotNull(vm.state.value.snackbarMessage)
    }

    @Test
    fun `폴링 SUCCEEDED - 서로 다른 두 섹션 중 한 후보만 돌아오면 제외 1`() = runTest(dispatcher) {
        // 서로 다른 섹션(s-1, s-2)의 소견 2개를 선택했는데 후보는 1개만 돌아왔다 → excluded = 1.
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(
                reviewResponse(
                    autoFinding("f-1", sectionId = "s-1"),
                    autoFinding("f-2", "또 다른 기준", sectionId = "s-2"),
                ),
            ),
            submitResult = ApiResult.Success(pendingJob()),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJob(sampleCandidate("c-1"))))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        vm.submitImprovement()
        testScheduler.advanceUntilIdle()

        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.CANDIDATES, vm.state.value.step)
        assertEquals(1, vm.state.value.excludedCandidateCount)
    }

    @Test
    fun `폴링 SUCCEEDED - 한 섹션의 소견 여럿이라도 후보 1개면 제외 0(오고지 금지)`() = runTest(dispatcher) {
        // 같은 섹션(s-1)에 소견 2개를 선택. 처치는 섹션당 1후보이므로 후보 1개가 정상 성공이고, 제외는 0이어야 한다.
        // (요청 소견 수로 계산하면 정상 성공을 "원본 유지"로 오고지한다 — L2 회귀 방지.)
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(
                reviewResponse(
                    autoFinding("f-1", sectionId = "s-1"),
                    autoFinding("f-2", "또 다른 기준", sectionId = "s-1"),
                ),
            ),
            submitResult = ApiResult.Success(pendingJob()),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJob(sampleCandidate("c-1"))))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()

        vm.submitImprovement()
        testScheduler.advanceUntilIdle()

        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.CANDIDATES, vm.state.value.step)
        assertEquals(0, vm.state.value.excludedCandidateCount)
    }

    // ── 후보 채택 ─────────────────────────────────────────────────────────────

    @Test
    fun `toggleCandidate - 체크 해제 후 재선택이 올바르게 동작한다`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Success(pendingJob()),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJob(sampleCandidate("c-1"), sampleCandidate("c-2"))))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()
        vm.submitImprovement()
        testScheduler.advanceUntilIdle()
        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        // 초기: 모두 선택.
        assertEquals(2, vm.state.value.selectedCandidates.size)

        vm.toggleCandidate("c-1")
        assertEquals(1, vm.state.value.selectedCandidates.size)
        assertEquals("c-2", vm.state.value.selectedCandidates[0].candidateId)
    }

    @Test
    fun `adoptSelected 성공 - ADOPTED 단계로 전환한다`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Success(pendingJob()),
            adoptResult = ApiResult.Success(sampleArtifact()),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJob(sampleCandidate())))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()
        vm.submitImprovement()
        testScheduler.advanceUntilIdle()
        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        vm.adoptSelected()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.ADOPTED, vm.state.value.step)
        assertEquals(listOf("c-1"), api.lastAdoptCandidateIds)
    }

    @Test
    fun `adoptSelected - 선택된 후보 없으면 호출하지 않는다`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Success(pendingJob()),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJob(sampleCandidate())))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()
        vm.submitImprovement()
        testScheduler.advanceUntilIdle()
        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        // 전체 해제.
        vm.toggleCandidate("c-1")
        vm.adoptSelected()
        testScheduler.advanceUntilIdle()

        assertEquals(0, api.adoptCount)
        assertEquals(QualityStep.CANDIDATES, vm.state.value.step)
    }

    @Test
    fun `adoptSelected 실패 - 스낵바 안내 후 CANDIDATES 유지`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Success(pendingJob()),
            adoptResult = ApiResult.Failure("서버 오류"),
        )
        api.getJobSequence.add(ApiResult.Success(succeededJob(sampleCandidate())))
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()
        vm.submitImprovement()
        testScheduler.advanceUntilIdle()
        advanceTimeBy(QualityReviewViewModel.POLL_INTERVAL_MS + 100)
        testScheduler.advanceUntilIdle()

        vm.adoptSelected()
        testScheduler.advanceUntilIdle()

        assertEquals(QualityStep.CANDIDATES, vm.state.value.step)
        assertNotNull(vm.state.value.snackbarMessage)
    }

    // ── 스낵바 소비 ───────────────────────────────────────────────────────────

    @Test
    fun `consumeSnackbar - 메시지를 null로 만든다`() = runTest(dispatcher) {
        val api = FakeQualityApi(
            reviewResult = ApiResult.Success(reviewResponse(autoFinding())),
            submitResult = ApiResult.Failure(
                message = "한도 초과",
                code = QualityReviewViewModel.QUOTA_EXCEEDED_CODE,
            ),
        )
        val vm = vm(api)
        vm.startReview()
        testScheduler.advanceUntilIdle()
        vm.submitImprovement()
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.snackbarMessage)
        vm.consumeSnackbar()
        assertNull(vm.state.value.snackbarMessage)
    }
}

/** [QualityReviewUiState]에서 SUGGESTION 소견만 꺼내는 편의 프로퍼티(테스트 가독성). */
private val QualityReviewUiState.suggestions: List<FindingUi>
    get() = findings.filter { it.treatmentKind == TreatmentKind.SUGGESTION }
