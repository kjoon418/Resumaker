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
}
