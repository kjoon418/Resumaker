package watson.resumaker.feature.artifact

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeArtifactApi
import watson.resumaker.model.dto.ArtifactSummaryResponse
import watson.resumaker.model.dto.GenerationJobResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.GenerationJobStatus
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun job(id: String, status: GenerationJobStatus, kind: ArtifactKind = ArtifactKind.RESUME) =
        GenerationJobResponse(
            jobId = id,
            kind = kind,
            status = status,
            artifactId = if (status == GenerationJobStatus.SUCCEEDED) "art-$id" else null,
            createdAt = "2026-06-22T00:00:00Z",
        )

    private fun artifact(id: String) = ArtifactSummaryResponse(
        id = id,
        kind = ArtifactKind.RESUME,
        targetCompany = "회사",
        createdAt = "2026-06-22T00:00:00Z",
        updatedAt = "2026-06-22T00:00:00Z",
    )

    @Test
    fun loadMergesJobsAndArtifacts() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),
            listArtifactsResult = ApiResult.Success(listOf(artifact("a-1"))),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(1, vm.state.value.jobs.size)
        assertEquals(1, vm.state.value.artifacts.size)
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun succeededJobIsNotRendered() = runTest(dispatcher) {
        // 중복 방지: SUCCEEDED 작업은 산출물 목록에 있으므로 작업 카드로 렌더하지 않는다.
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(
                listOf(
                    job("j-done", GenerationJobStatus.SUCCEEDED),
                    job("j-failed", GenerationJobStatus.FAILED),
                ),
            ),
            listArtifactsResult = ApiResult.Success(listOf(artifact("a-1"))),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        val rendered = vm.state.value.renderJobs
        assertEquals(listOf("j-failed"), rendered.map { it.jobId })
    }

    @Test
    fun pollingReflectsRunningToSucceededTransition() = runTest(dispatcher) {
        // 가짜 Api가 호출마다 다른 상태를 돌려준다: RUNNING → SUCCEEDED. 폴링이 전환을 반영해야 한다.
        val api = FakeArtifactApi(
            listArtifactsResult = ApiResult.Success(emptyList()),
        )
        api.listJobsSequence = ArrayDeque(
            listOf(
                ApiResult.Success(listOf(job("j-1", GenerationJobStatus.RUNNING))),  // 최초 load
                ApiResult.Success(listOf(job("j-1", GenerationJobStatus.SUCCEEDED))), // 1차 폴링
            ),
        )
        // 완료 후 산출물도 나타나도록 갱신(폴링 종료 후 상태 확인용).
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        // 폴링이 SUCCEEDED를 받으면 활성 작업이 0이 되어 루프가 멈추고, SUCCEEDED는 렌더되지 않는다.
        assertFalse(vm.state.value.hasActiveJobs)
        assertTrue(vm.state.value.renderJobs.isEmpty())
        // listJobs가 최소 2회(load + 폴링 1회) 호출됐다.
        assertTrue(api.listJobsCallCount >= 2)
    }

    @Test
    fun pollingStopsWhenNoActiveJobs() = runTest(dispatcher) {
        // 활성 작업이 없으면 폴링을 가동하지 않는다(추가 호출 없음).
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),
            listArtifactsResult = ApiResult.Success(listOf(artifact("a-1"))),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.hasActiveJobs)
        // load 1회만 호출(폴링 미가동).
        assertEquals(1, api.listJobsCallCount)
    }

    @Test
    fun requestDeleteJobOpensConfirmationAndDoesNotCallApi() = runTest(dispatcher) {
        // UX-08: '기록 삭제'는 무확인 즉시 삭제가 아니라 확인 다이얼로그를 먼저 띄운다(파괴적 동작 일관성).
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),
            deleteJobResult = ApiResult.Success(Unit),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDeleteJob(job("j-1", GenerationJobStatus.FAILED))
        testScheduler.advanceUntilIdle()

        // 다이얼로그만 열렸고 아직 삭제 API는 호출되지 않았다.
        assertEquals("j-1", vm.state.value.pendingDeleteJob?.jobId)
        assertNull(api.deletedJobId)
        assertEquals(listOf("j-1"), vm.state.value.jobs.map { it.jobId })
    }

    @Test
    fun cancelDeleteJobClosesConfirmationWithoutDeleting() = runTest(dispatcher) {
        // UX-08: 확인 취소 시 아무것도 삭제하지 않고 다이얼로그만 닫는다.
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),
            deleteJobResult = ApiResult.Success(Unit),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDeleteJob(job("j-1", GenerationJobStatus.FAILED))
        vm.cancelDeleteJob()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.pendingDeleteJob)
        assertNull(api.deletedJobId)
        assertEquals(listOf("j-1"), vm.state.value.jobs.map { it.jobId })
    }

    @Test
    fun confirmingDeleteRemovesJobAndClearsPending() = runTest(dispatcher) {
        // UX-08: 확인하면 실제 삭제가 일어나고 대기 상태가 비워진다.
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),
            deleteJobResult = ApiResult.Success(Unit),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDeleteJob(job("j-1", GenerationJobStatus.FAILED))
        vm.deleteJob(job("j-1", GenerationJobStatus.FAILED))
        testScheduler.advanceUntilIdle()

        assertEquals("j-1", api.deletedJobId)
        assertNull(vm.state.value.pendingDeleteJob)
        assertTrue(vm.state.value.jobs.isEmpty())
    }

    @Test
    fun deleteJobRemovesFromList() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(
                listOf(
                    job("j-1", GenerationJobStatus.FAILED),
                    job("j-2", GenerationJobStatus.FAILED),
                ),
            ),
            deleteJobResult = ApiResult.Success(Unit),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.deleteJob(job("j-1", GenerationJobStatus.FAILED))
        testScheduler.advanceUntilIdle()

        assertEquals("j-1", api.deletedJobId)
        assertEquals(listOf("j-2"), vm.state.value.jobs.map { it.jobId })
    }

    @Test
    fun retryJobReplacesFailedWithNewActiveJob() = runTest(dispatcher) {
        // IN_PLACE 다시 만들기: 서버가 실패 작업을 새 PENDING으로 교체하므로, 성공 후 목록을 다시 부르면 실패
        // 카드가 사라지고 새 진행 카드가 나타난다. 새 작업이 곧 완료(SUCCEEDED)되면 폴링이 멈춘다.
        val api = FakeArtifactApi(
            listArtifactsResult = ApiResult.Success(emptyList()),
            retryJobResult = ApiResult.Success(job("j-2", GenerationJobStatus.PENDING)),
        )
        api.listJobsSequence = ArrayDeque(
            listOf(
                ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),    // 최초 load
                ApiResult.Success(listOf(job("j-2", GenerationJobStatus.PENDING))),   // 재요청 후 refresh
                ApiResult.Success(listOf(job("j-2", GenerationJobStatus.SUCCEEDED))), // 폴링 1회 → 종료
            ),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.retryJob(job("j-1", GenerationJobStatus.FAILED))
        testScheduler.advanceUntilIdle()

        // 실패 작업으로 재요청 API가 호출됐고, 옛 실패 카드는 사라지고(=목록에서 j-1 제거), 진행 상태는 비활성화됐다.
        assertEquals("j-1", api.retriedJobId)
        assertEquals(1, api.retryJobCount)
        assertTrue(vm.state.value.jobs.none { it.jobId == "j-1" })
        assertTrue(vm.state.value.retryingJobIds.isEmpty())
        assertTrue(vm.state.value.renderJobs.isEmpty()) // SUCCEEDED는 렌더하지 않음
    }

    @Test
    fun retryJobFailureShowsSnackbarAndKeepsFailedCard() = runTest(dispatcher) {
        // IN_PLACE가 아닌 작업·한도 초과 등으로 재요청이 거절되면 스낵바로 안내하고 실패 카드는 그대로 둔다.
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(listOf(job("j-1", GenerationJobStatus.FAILED))),
            listArtifactsResult = ApiResult.Success(emptyList()),
            retryJobResult = ApiResult.Failure("이 작업은 같은 정보로 다시 만들 수 없어요."),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.retryJob(job("j-1", GenerationJobStatus.FAILED))
        testScheduler.advanceUntilIdle()

        assertEquals("이 작업은 같은 정보로 다시 만들 수 없어요.", vm.state.value.snackbarMessage)
        assertEquals(listOf("j-1"), vm.state.value.jobs.map { it.jobId })
        assertTrue(vm.state.value.retryingJobIds.isEmpty())
    }

    @Test
    fun emptyStateWhenNoJobsOrArtifacts() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            listJobsResult = ApiResult.Success(emptyList()),
            listArtifactsResult = ApiResult.Success(emptyList()),
        )
        val vm = ArtifactListViewModel(api)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.loading)
    }
}
