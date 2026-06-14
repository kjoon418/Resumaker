package watson.resumaker.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeExperienceApi
import watson.resumaker.fake.FakeTargetApi
import watson.resumaker.fake.sampleExperience
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun target(id: String) = TargetResponse(id = id, recruitDirection = "방향", companyName = null, jobTitle = null)

    @Test
    fun loadsBothSectionsInParallelAndPreviewsCapAtThree() = runTest(dispatcher) {
        val experiences = (1..5).map { sampleExperience(id = "e$it") }
        val targets = (1..4).map { target("t$it") }
        val vm = HomeViewModel(
            FakeExperienceApi(getAllResult = ApiResult.Success(experiences)),
            FakeTargetApi(getAllResult = ApiResult.Success(targets)),
        )
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(!state.loading)
        assertEquals(5, state.experiences.size)
        assertEquals(4, state.targets.size)
        // 미리보기는 최대 3개.
        assertEquals(3, state.experiencePreview.size)
        assertEquals(3, state.targetPreview.size)
        assertNull(state.errorMessage)
    }

    @Test
    fun partialFailureExperiencesErrorTakesPriorityAndKeepsLoadedTargets() = runTest(dispatcher) {
        val vm = HomeViewModel(
            FakeExperienceApi(getAllResult = ApiResult.Failure("경험 로드 실패")),
            FakeTargetApi(getAllResult = ApiResult.Success(listOf(target("t1")))),
        )
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        // 경험 실패 메시지가 우선 노출되어야 한다(HomeViewModel.kt 부분 실패 우선순위).
        assertEquals("경험 로드 실패", state.errorMessage)
        // 성공한 목표는 그대로 반영.
        assertEquals(1, state.targets.size)
    }

    @Test
    fun partialFailureFallsBackToTargetErrorWhenExperiencesSucceed() = runTest(dispatcher) {
        val vm = HomeViewModel(
            FakeExperienceApi(getAllResult = ApiResult.Success(listOf(sampleExperience()))),
            FakeTargetApi(getAllResult = ApiResult.Failure("목표 로드 실패")),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("목표 로드 실패", vm.state.value.errorMessage)
        assertEquals(1, vm.state.value.experiences.size)
    }
}
