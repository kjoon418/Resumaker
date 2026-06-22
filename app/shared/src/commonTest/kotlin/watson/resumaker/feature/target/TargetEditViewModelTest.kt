package watson.resumaker.feature.target

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeTargetApi
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.type.StrategyStatus
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
class TargetEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun missingRecruitDirectionShowsErrorAndSkipsApi() = runTest(dispatcher) {
        val api = FakeTargetApi()
        val vm = TargetEditViewModel(api, targetId = null)
        vm.save()

        assertNotNull(vm.state.value.recruitDirectionError)
        assertNull(api.lastCreate)
    }

    @Test
    fun validCreateSendsTrimmedOptionalsAsNullWhenBlank() = runTest(dispatcher) {
        val api = FakeTargetApi()
        val vm = TargetEditViewModel(api, targetId = null)
        vm.onRecruitDirectionChange("백엔드 엔지니어를 찾습니다")
        vm.save()
        testScheduler.advanceUntilIdle()

        val request = api.lastCreate
        assertNotNull(request)
        assertEquals("백엔드 엔지니어를 찾습니다", request.recruitDirection)
        assertNull(request.companyName)
        assertNull(request.jobTitle)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun optionalsPassedWhenProvided() = runTest(dispatcher) {
        val api = FakeTargetApi()
        val vm = TargetEditViewModel(api, targetId = null)
        vm.onCompanyChange("토스")
        vm.onJobTitleChange("백엔드")
        vm.onRecruitDirectionChange("방향")
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals("토스", api.lastCreate?.companyName)
        assertEquals("백엔드", api.lastCreate?.jobTitle)
    }

    // UX-4: 로드 실패 후 retryLoad가 같은 id를 다시 불러와 복구한다.
    @Test
    fun retryLoadReloadsAndClearsLoadErrorOnSuccess() = runTest(dispatcher) {
        val api = FakeTargetApi(getOneResult = ApiResult.Failure("불러오기 실패"))
        val vm = TargetEditViewModel(api, targetId = "t-99")
        testScheduler.advanceUntilIdle()
        assertEquals("불러오기 실패", vm.state.value.loadError)

        api.getOneResult = ApiResult.Success(
            TargetResponse(id = "t-99", recruitDirection = "방향", companyName = "토스", jobTitle = "백엔드"),
        )
        vm.retryLoad()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.loadError)
        assertEquals("방향", vm.state.value.recruitDirection)
    }

    // 생성 저장 후 메시지는 "전략 분석 중" 안내를 담는다.
    @Test
    fun createSavedMessageMentionsStrategyAnalysis() = runTest(dispatcher) {
        val vm = TargetEditViewModel(FakeTargetApi(), targetId = null)
        vm.onRecruitDirectionChange("백엔드 엔지니어를 찾습니다")
        vm.save()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        assertEquals("목표를 추가했어요. 작성 전략을 분석하는 중이에요.", vm.state.value.savedMessage)
        // 생성 모드엔 전략 재분석 InfoCard를 보이지 않는다.
        assertFalse(vm.state.value.showStrategyReanalyzeInfo)
    }

    // 전략이 있던(READY) 목표 수정 시 InfoCard를 보이고, 저장 후 "다시 분석 중" 메시지를 쓴다.
    @Test
    fun editWithReadyStrategyShowsInfoAndReanalyzeMessage() = runTest(dispatcher) {
        val api = FakeTargetApi(
            getOneResult = ApiResult.Success(
                TargetResponse(id = "t-1", recruitDirection = "방향", strategyStatus = StrategyStatus.READY),
            ),
        )
        val vm = TargetEditViewModel(api, targetId = "t-1")
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.showStrategyReanalyzeInfo)

        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("목표를 수정했어요. 변경된 내용으로 전략을 다시 분석하는 중이에요.", vm.state.value.savedMessage)
    }

    // 전략이 아직 없던(분석 중) 목표 수정 시 InfoCard는 숨기고, 일반 수정 메시지를 쓴다.
    @Test
    fun editWithoutReadyStrategyHidesInfoAndUsesPlainMessage() = runTest(dispatcher) {
        val api = FakeTargetApi(
            getOneResult = ApiResult.Success(
                TargetResponse(id = "t-1", recruitDirection = "방향", strategyStatus = StrategyStatus.EXTRACTING),
            ),
        )
        val vm = TargetEditViewModel(api, targetId = "t-1")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.showStrategyReanalyzeInfo)
        assertEquals("목표를 수정했어요.", vm.state.value.savedMessage)
    }
}
