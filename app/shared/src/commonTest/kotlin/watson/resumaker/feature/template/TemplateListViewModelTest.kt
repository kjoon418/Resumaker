package watson.resumaker.feature.template

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeTemplateApi
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.type.SectionCharacter
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun template(id: String) = TemplateResponse(
        id = id,
        name = "양식 $id",
        sections = listOf(SectionResponse("요약", SectionCharacter.SUMMARY, false)),
    )

    @Test
    fun loadSuccessPopulatesItems() = runTest(dispatcher) {
        val vm = TemplateListViewModel(
            FakeTemplateApi(getAllResult = ApiResult.Success(listOf(template("a"), template("b")))),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.items.size)
        assertTrue(!vm.state.value.loading)
    }

    @Test
    fun loadFailureSetsErrorMessage() = runTest(dispatcher) {
        val vm = TemplateListViewModel(FakeTemplateApi(getAllResult = ApiResult.Failure("양식 로드 실패")))
        testScheduler.advanceUntilIdle()

        assertEquals("양식 로드 실패", vm.state.value.errorMessage)
    }

    @Test
    fun confirmDeleteRemovesItemOptimistically() = runTest(dispatcher) {
        val toDelete = template("del")
        val api = FakeTemplateApi(getAllResult = ApiResult.Success(listOf(toDelete, template("keep"))))
        val vm = TemplateListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(toDelete)
        assertNotNull(vm.state.value.pendingDelete)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals("del", api.deletedId)
        assertEquals(listOf("keep"), vm.state.value.items.map { it.id })
    }

    // UX-5: 삭제 실패는 retryableDelete를 채워 "다시 시도" 액션을 노출하고, retryDelete가 복구한다.
    @Test
    fun deleteFailureExposesRetryAndRetrySucceeds() = runTest(dispatcher) {
        val toDelete = template("del")
        val api = FakeTemplateApi(
            getAllResult = ApiResult.Success(listOf(toDelete, template("keep"))),
            deleteResult = ApiResult.Failure("삭제 실패"),
        )
        val vm = TemplateListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(toDelete)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertEquals("삭제 실패", vm.state.value.snackbarMessage)
        assertNotNull(vm.state.value.retryableDelete)
        assertEquals(listOf("del", "keep"), vm.state.value.items.map { it.id })

        api.deleteResult = ApiResult.Success(Unit)
        vm.retryDelete()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("keep"), vm.state.value.items.map { it.id })
        assertNull(vm.state.value.retryableDelete)
    }

    // CQ-2: 스낵바를 액션 없이 dismiss하면 retryableDelete가 클리어되어야 한다.
    @Test
    fun clearRetryableDeleteResetsStaleRetry() = runTest(dispatcher) {
        val toDelete = template("del")
        val api = FakeTemplateApi(
            getAllResult = ApiResult.Success(listOf(toDelete, template("keep"))),
            deleteResult = ApiResult.Failure("삭제 실패"),
        )
        val vm = TemplateListViewModel(api)
        testScheduler.advanceUntilIdle()

        vm.requestDelete(toDelete)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.retryableDelete)

        vm.consumeSnackbar()
        vm.clearRetryableDelete()

        assertNull(vm.state.value.retryableDelete)
        assertEquals(listOf("del", "keep"), vm.state.value.items.map { it.id })
    }
}
