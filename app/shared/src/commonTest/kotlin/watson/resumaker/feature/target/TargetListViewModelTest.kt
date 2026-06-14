package watson.resumaker.feature.target

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeTargetApi
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TargetListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun target(id: String) = TargetResponse(id = id, recruitDirection = "방향 $id", companyName = null, jobTitle = null)

    @Test
    fun loadSuccessPopulatesItems() = runTest(dispatcher) {
        val vm = TargetListViewModel(
            FakeTargetApi(getAllResult = ApiResult.Success(listOf(target("a"), target("b")))),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        assertTrue(!vm.state.value.loading)
    }

    @Test
    fun loadFailureSetsErrorMessage() = runTest(dispatcher) {
        val vm = TargetListViewModel(FakeTargetApi(getAllResult = ApiResult.Failure("목표 로드 실패")))
        testScheduler.advanceUntilIdle()

        assertEquals("목표 로드 실패", vm.state.value.errorMessage)
    }

    @Test
    fun confirmDeleteRemovesItemOptimistically() = runTest(dispatcher) {
        val toDelete = target("del")
        val api = FakeTargetApi(getAllResult = ApiResult.Success(listOf(toDelete, target("keep"))))
        val vm = TargetListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(toDelete)
        assertNotNull(vm.state.value.pendingDelete)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals("del", api.deletedId)
        assertEquals(listOf("keep"), vm.state.value.items.map { it.id })
    }
}
