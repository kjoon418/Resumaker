package watson.resumaker.feature.experience

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeExperienceApi
import watson.resumaker.fake.sampleExperience
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadSuccessPopulatesItems() = runTest(dispatcher) {
        val api = FakeExperienceApi(getAllResult = ApiResult.Success(listOf(sampleExperience(id = "a"), sampleExperience(id = "b"))))
        val vm = ExperienceListViewModel(api)
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        assertTrue(!vm.state.value.loading)
    }

    @Test
    fun loadFailureSetsErrorMessage() = runTest(dispatcher) {
        val api = FakeExperienceApi(getAllResult = ApiResult.Failure("불러오기 실패"))
        val vm = ExperienceListViewModel(api)
        testScheduler.advanceUntilIdle()

        assertEquals("불러오기 실패", vm.state.value.errorMessage)
    }

    @Test
    fun confirmDeleteRemovesItemOptimistically() = runTest(dispatcher) {
        val target = sampleExperience(id = "del")
        val api = FakeExperienceApi(getAllResult = ApiResult.Success(listOf(target, sampleExperience(id = "keep"))))
        val vm = ExperienceListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(target)
        assertNotNull(vm.state.value.pendingDelete)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals("del", api.deletedId)
        assertEquals(listOf("keep"), vm.state.value.items.map { it.id })
    }

    // UX-5: 삭제 실패는 retryableDelete를 채워 "다시 시도" 액션을 노출하고, retryDelete가 복구한다.
    @Test
    fun deleteFailureExposesRetryAndRetrySucceeds() = runTest(dispatcher) {
        val target = sampleExperience(id = "del")
        val api = FakeExperienceApi(
            getAllResult = ApiResult.Success(listOf(target, sampleExperience(id = "keep"))),
            deleteResult = ApiResult.Failure("삭제 실패"),
        )
        val vm = ExperienceListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(target)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals("삭제 실패", vm.state.value.snackbarMessage)
        assertNotNull(vm.state.value.retryableDelete)
        assertEquals(listOf("del", "keep"), vm.state.value.items.map { it.id })

        // 다음 삭제는 성공하도록 교체 후 재시도.
        api.deleteResult = ApiResult.Success(Unit)
        vm.retryDelete()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("keep"), vm.state.value.items.map { it.id })
        assertNull(vm.state.value.retryableDelete)
    }

    // CQ-2: 스낵바를 액션 없이 dismiss하면 retryableDelete가 클리어되어야 한다.
    @Test
    fun clearRetryableDeleteResetsStaleRetry() = runTest(dispatcher) {
        val target = sampleExperience(id = "del")
        val api = FakeExperienceApi(
            getAllResult = ApiResult.Success(listOf(target, sampleExperience(id = "keep"))),
            deleteResult = ApiResult.Failure("삭제 실패"),
        )
        val vm = ExperienceListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(target)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.retryableDelete)

        // 사용자가 액션 없이 dismiss: clearRetryableDelete 호출.
        vm.consumeSnackbar()
        vm.clearRetryableDelete()

        assertNull(vm.state.value.retryableDelete)
        // 목록은 그대로 유지(삭제는 실패했으므로).
        assertEquals(listOf("del", "keep"), vm.state.value.items.map { it.id })
    }
}
