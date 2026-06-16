package watson.resumaker.feature.template

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeTemplateInterpretApi
import watson.resumaker.model.dto.InterpretResponse
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.model.type.SectionCharacter
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateInterpretViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun sections() = listOf(
        SectionResponse("한 줄 자기소개", SectionCharacter.SUMMARY, false),
        SectionResponse("주요 경력", SectionCharacter.CAREER, true),
    )

    @Test
    fun blankInputSkipsApiAndFallsBack() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi()
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("   ")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        assertEquals(0, api.interpretCount)
        assertNotNull(vm.state.value.pastedTextError)
        assertIs<InterpretGateState.Fallback>(vm.state.value.gate)
    }

    @Test
    fun interpretedWithSectionsEntersGate() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(
            interpretResult = ApiResult.Success(
                InterpretResponse(status = InterpretResponse.STATUS_INTERPRETED, sections = sections()),
            ),
        )
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        val gate = vm.state.value.gate
        assertIs<InterpretGateState.Gate>(gate)
        assertEquals(2, gate.sections.size)
    }

    @Test
    fun interpretedButEmptySectionsFallsBack() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(
            interpretResult = ApiResult.Success(
                InterpretResponse(status = InterpretResponse.STATUS_INTERPRETED, sections = emptyList()),
            ),
        )
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        assertIs<InterpretGateState.Fallback>(vm.state.value.gate)
    }

    @Test
    fun unavailableFallsBack() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(
            interpretResult = ApiResult.Success(
                InterpretResponse(status = InterpretResponse.STATUS_UNAVAILABLE),
            ),
        )
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        assertIs<InterpretGateState.Fallback>(vm.state.value.gate)
    }

    @Test
    fun apiFailureFallsBackWithSnackbar() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(interpretResult = ApiResult.Failure("해석 실패"))
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        assertIs<InterpretGateState.Fallback>(vm.state.value.gate)
        assertEquals("해석 실패", vm.state.value.snackbarMessage)
    }

    @Test
    fun confirmGateWithBlankNameShowsErrorAndStaysInGate() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(
            interpretResult = ApiResult.Success(
                InterpretResponse(status = InterpretResponse.STATUS_INTERPRETED, sections = sections()),
            ),
        )
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        vm.confirmGate()

        assertNotNull(vm.state.value.templateNameError)
        assertIs<InterpretGateState.Gate>(vm.state.value.gate)
    }

    @Test
    fun confirmGateWithValidNameConfirms() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(
            interpretResult = ApiResult.Success(
                InterpretResponse(status = InterpretResponse.STATUS_INTERPRETED, sections = sections()),
            ),
        )
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        vm.onTemplateNameChange("토스 백엔드 지원용")
        vm.confirmGate()

        val gate = vm.state.value.gate
        assertIs<InterpretGateState.Confirmed>(gate)
        assertEquals("토스 백엔드 지원용", gate.templateName)
        assertEquals(2, gate.sections.size)
    }

    @Test
    fun confirmGateIsNoOpWhenNotInGate() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi()
        val vm = TemplateInterpretViewModel(api)
        // 초기 상태(Idle)에서 confirmGate는 아무 효과가 없어야 한다.
        vm.onTemplateNameChange("토스 백엔드 지원용")
        vm.confirmGate()

        assertIs<InterpretGateState.Idle>(vm.state.value.gate)
    }

    @Test
    fun consumeConfirmedReturnsToIdle() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi(
            interpretResult = ApiResult.Success(
                InterpretResponse(status = InterpretResponse.STATUS_INTERPRETED, sections = sections()),
            ),
        )
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("회사 양식 텍스트")
        vm.interpret()
        testScheduler.advanceUntilIdle()
        vm.onTemplateNameChange("토스 백엔드 지원용")
        vm.confirmGate()
        assertIs<InterpretGateState.Confirmed>(vm.state.value.gate)

        vm.consumeConfirmed()
        assertIs<InterpretGateState.Idle>(vm.state.value.gate)
    }

    @Test
    fun blankInputDoesNotCallApiEvenAfterWhitespaceTyped() = runTest(dispatcher) {
        val api = FakeTemplateInterpretApi()
        val vm = TemplateInterpretViewModel(api)
        vm.onPastedTextChange("")
        vm.interpret()
        testScheduler.advanceUntilIdle()

        assertEquals(0, api.interpretCount)
        assertTrue(vm.state.value.gate is InterpretGateState.Fallback)
    }
}
