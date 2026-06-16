package watson.resumaker.feature.template

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeTemplatePresetApi
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.model.dto.TemplatePresetResponse
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
class TemplatePresetViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun preset(key: String) = TemplatePresetResponse(
        key = key,
        name = "프리셋 $key",
        sections = listOf(SectionResponse("요약", SectionCharacter.SUMMARY, false)),
    )

    @Test
    fun loadSuccessPopulatesPresets() = runTest(dispatcher) {
        val vm = TemplatePresetViewModel(
            FakeTemplatePresetApi(getAllResult = ApiResult.Success(listOf(preset("a"), preset("b")))),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.presets.size)
        assertTrue(!vm.state.value.loading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun loadFailureSetsErrorMessage() = runTest(dispatcher) {
        val vm = TemplatePresetViewModel(
            FakeTemplatePresetApi(getAllResult = ApiResult.Failure("프리셋 로드 실패")),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("프리셋 로드 실패", vm.state.value.errorMessage)
        assertTrue(!vm.state.value.loading)
    }

    @Test
    fun selectPresetSetsSelected() = runTest(dispatcher) {
        val target = preset("a")
        val vm = TemplatePresetViewModel(
            FakeTemplatePresetApi(getAllResult = ApiResult.Success(listOf(target))),
        )
        testScheduler.advanceUntilIdle()

        vm.selectPreset(target)
        assertNotNull(vm.state.value.selectedPreset)
        assertEquals("a", vm.state.value.selectedPreset?.key)
    }

    @Test
    fun consumeSelectedPresetClearsSelection() = runTest(dispatcher) {
        val target = preset("a")
        val vm = TemplatePresetViewModel(
            FakeTemplatePresetApi(getAllResult = ApiResult.Success(listOf(target))),
        )
        testScheduler.advanceUntilIdle()

        vm.selectPreset(target)
        vm.consumeSelectedPreset()
        assertNull(vm.state.value.selectedPreset)
    }
}
