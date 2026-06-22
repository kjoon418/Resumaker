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
