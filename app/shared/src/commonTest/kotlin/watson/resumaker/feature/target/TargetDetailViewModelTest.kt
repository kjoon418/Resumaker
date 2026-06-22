package watson.resumaker.feature.target

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeTargetApi
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.WritingStrategyResponse
import watson.resumaker.model.type.StrategyStatus
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TargetDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun target(
        id: String = "t-1",
        status: StrategyStatus = StrategyStatus.PENDING,
        strategy: WritingStrategyResponse? = null,
    ) = TargetResponse(
        id = id,
        recruitDirection = "백엔드 엔지니어를 찾습니다",
        companyName = "토스",
        jobTitle = "백엔드",
        strategyStatus = status,
        writingStrategy = strategy,
    )

    @Test
    fun loadSuccessPopulatesTarget() = runTest(dispatcher) {
        val api = FakeTargetApi(getOneResult = ApiResult.Success(target(status = StrategyStatus.READY)))
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()

        assertEquals("t-1", vm.state.value.target?.id)
        assertEquals(StrategyStatus.READY, vm.state.value.strategyStatus)
        assertTrue(!vm.state.value.loading)
    }

    @Test
    fun loadFailureWhenNoTargetSetsErrorMessage() = runTest(dispatcher) {
        val api = FakeTargetApi(getOneResult = ApiResult.Failure("소유 격리 실패"))
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()

        assertEquals("소유 격리 실패", vm.state.value.errorMessage)
        assertEquals(null, vm.state.value.target)
    }

    // PENDING/EXTRACTING이면 폴링을 시작하고, READY로 전환되면 반영하고 폴링을 멈춘다.
    @Test
    fun pollingReflectsTransitionToReadyAndStops() = runTest(dispatcher) {
        val readyStrategy = WritingStrategyResponse(keywords = listOf("Kotlin"), summary = "요약")
        val api = FakeTargetApi().apply {
            getOneSequence = ArrayDeque(
                listOf(
                    ApiResult.Success(target(status = StrategyStatus.PENDING)),
                    ApiResult.Success(target(status = StrategyStatus.EXTRACTING)),
                    ApiResult.Success(target(status = StrategyStatus.READY, strategy = readyStrategy)),
                ),
            )
        }
        val vm = TargetDetailViewModel(api, "t-1")
        // 초기 로드(refresh)만 실행: delay 없이 즉시 완료되는 코루틴 태스크만 소비한다.
        // advanceUntilIdle()은 스케줄러 상의 지연 포함 모두 소비하므로 여기서는 쓰지 않는다.
        advanceTimeBy(1)
        // 초기 로드: PENDING. 폴링 루프는 delay(3000) 대기 중.
        assertEquals(StrategyStatus.PENDING, vm.state.value.strategyStatus)

        // 폴링 1회(EXTRACTING).
        advanceTimeBy(TargetDetailViewModel.POLL_INTERVAL_MS)
        // 폴링 2회(READY).
        advanceTimeBy(TargetDetailViewModel.POLL_INTERVAL_MS)

        assertEquals(StrategyStatus.READY, vm.state.value.strategyStatus)
        assertEquals(readyStrategy, vm.state.value.target?.writingStrategy)

        // READY 도달 후에는 더 폴링하지 않는다 — getOne 호출 수가 더 늘지 않는다.
        val callsAfterReady = api.getOneCount
        advanceTimeBy(TargetDetailViewModel.POLL_INTERVAL_MS * 3)
        testScheduler.advanceUntilIdle()
        assertEquals(callsAfterReady, api.getOneCount)
    }

    // READY 상태로 진입하면 폴링하지 않는다(getOne 1회만).
    @Test
    fun readyOnLoadDoesNotPoll() = runTest(dispatcher) {
        val api = FakeTargetApi(getOneResult = ApiResult.Success(target(status = StrategyStatus.READY)))
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()

        advanceTimeBy(TargetDetailViewModel.POLL_INTERVAL_MS * 3)
        testScheduler.advanceUntilIdle()
        assertEquals(1, api.getOneCount)
    }

    // FAILED → retry → 202면 낙관적으로 PENDING으로 돌리고 폴링을 재개해 READY를 확인한다.
    @Test
    fun retryAfterFailedRequeuesAndRepolls() = runTest(dispatcher) {
        val api = FakeTargetApi(getOneResult = ApiResult.Success(target(status = StrategyStatus.FAILED)))
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()
        assertEquals(StrategyStatus.FAILED, vm.state.value.strategyStatus)

        // retry 이후 폴링 시퀀스: EXTRACTING → READY.
        api.getOneSequence = ArrayDeque(
            listOf(
                ApiResult.Success(target(status = StrategyStatus.EXTRACTING)),
                ApiResult.Success(target(status = StrategyStatus.READY, strategy = WritingStrategyResponse(tone = "정중"))),
            ),
        )
        vm.retryStrategy()
        // retryStrategy 코루틴 본문만 실행(delay 없음). advanceUntilIdle()은 폴 delay까지 소비하므로 쓰지 않는다.
        advanceTimeBy(1)

        assertEquals("t-1", api.retryStrategyId)
        assertEquals(1, api.retryStrategyCount)
        // 낙관적 전환: 즉시 PENDING(분석 중).
        assertEquals(StrategyStatus.PENDING, vm.state.value.strategyStatus)

        // 폴링 1회(EXTRACTING).
        advanceTimeBy(TargetDetailViewModel.POLL_INTERVAL_MS)
        // 폴링 2회(READY).
        advanceTimeBy(TargetDetailViewModel.POLL_INTERVAL_MS)

        assertEquals(StrategyStatus.READY, vm.state.value.strategyStatus)
    }

    // retry가 실패(소유 격리 404 등)하면 스낵바로 표면화하고 재시도 플래그를 푼다.
    @Test
    fun retryFailureSurfacesSnackbar() = runTest(dispatcher) {
        val api = FakeTargetApi(
            getOneResult = ApiResult.Success(target(status = StrategyStatus.FAILED)),
            retryStrategyResult = ApiResult.Failure("권한이 없어요"),
        )
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()

        vm.retryStrategy()
        testScheduler.advanceUntilIdle()

        assertEquals("권한이 없어요", vm.state.value.snackbarMessage)
        assertTrue(!vm.state.value.retrying)
    }

    @Test
    fun toggleDirectionExpandedFlips() = runTest(dispatcher) {
        val api = FakeTargetApi(getOneResult = ApiResult.Success(target(status = StrategyStatus.READY)))
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()

        assertTrue(!vm.state.value.directionExpanded)
        vm.toggleDirectionExpanded()
        assertTrue(vm.state.value.directionExpanded)
        vm.toggleDirectionExpanded()
        assertTrue(!vm.state.value.directionExpanded)
    }

    @Test
    fun strategyReadyExposesStrategyFields() = runTest(dispatcher) {
        val strategy = WritingStrategyResponse(
            keywords = listOf("Kotlin", "Spring"),
            tone = "자신감 있는",
            emphasize = listOf("성과"),
            avoid = listOf("과장"),
            summary = "백엔드 채용",
        )
        val api = FakeTargetApi(getOneResult = ApiResult.Success(target(status = StrategyStatus.READY, strategy = strategy)))
        val vm = TargetDetailViewModel(api, "t-1")
        testScheduler.advanceUntilIdle()

        val loaded = vm.state.value.target?.writingStrategy
        assertNotNull(loaded)
        assertEquals(listOf("Kotlin", "Spring"), loaded.keywords)
    }
}
