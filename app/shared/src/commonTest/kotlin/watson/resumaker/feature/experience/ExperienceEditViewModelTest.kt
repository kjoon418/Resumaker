package watson.resumaker.feature.experience

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeExperienceApi
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.type.ExperienceType
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun savingWithMissingRequiredFieldsShowsAllInlineErrorsAndSkipsApi() = runTest(dispatcher) {
        val api = FakeExperienceApi()
        val vm = ExperienceEditViewModel(api, experienceId = null)

        vm.save()

        val state = vm.state.value
        assertNotNull(state.titleError)
        assertNotNull(state.typeError)
        assertNotNull(state.bodyError)
        assertNull(api.lastCreate)
    }

    @Test
    fun bodyMissingUsesDomainCopy() = runTest(dispatcher) {
        val vm = ExperienceEditViewModel(FakeExperienceApi(), experienceId = null)
        vm.onTitleChange("제목")
        vm.onTypeChange(ExperienceType.PROJECT)
        vm.save()

        assertTrue(vm.state.value.bodyError!!.contains("무슨 일을 했는지"))
    }

    @Test
    fun validCreateSendsRequestWithoutEmptyDetail() = runTest(dispatcher) {
        val api = FakeExperienceApi()
        val vm = ExperienceEditViewModel(api, experienceId = null)
        vm.onTitleChange("결제 개선")
        vm.onTypeChange(ExperienceType.PROJECT)
        vm.onBodyChange("응답시간 40% 개선")
        vm.save()
        testScheduler.advanceUntilIdle()

        val request = api.lastCreate
        assertNotNull(request)
        assertEquals("결제 개선", request.title)
        assertEquals(ExperienceType.PROJECT, request.type)
        // 선택 항목을 하나도 안 넣었으므로 detail은 null이어야 한다.
        assertNull(request.detail)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun detailBuiltWhenOptionalProvided() = runTest(dispatcher) {
        val api = FakeExperienceApi()
        val vm = ExperienceEditViewModel(api, experienceId = null)
        vm.onTitleChange("t")
        vm.onTypeChange(ExperienceType.JOB)
        vm.onBodyChange("b")
        vm.onSkillInputChange("데이터분석")
        vm.addSkill()
        vm.save()
        testScheduler.advanceUntilIdle()

        val detail = api.lastCreate?.detail
        assertNotNull(detail)
        assertEquals(listOf("데이터분석"), detail.skillTags)
    }

    @Test
    fun addSkillDeduplicatesAndRemoveWorks() = runTest(dispatcher) {
        val vm = ExperienceEditViewModel(FakeExperienceApi(), experienceId = null)
        vm.onSkillInputChange("Kotlin")
        vm.addSkill()
        vm.onSkillInputChange("kotlin")
        vm.addSkill()
        assertEquals(1, vm.state.value.skillTags.size)

        vm.removeSkill("Kotlin")
        assertTrue(vm.state.value.skillTags.isEmpty())
    }

    // --- 수정(edit) 모드 ---

    private fun existingWithDetail() = ExperienceResponse(
        id = "e-99",
        title = "기존 경험",
        type = ExperienceType.JOB,
        body = "기존 본문",
        situation = "상황",
        action = "행동",
        result = "결과",
        periodStart = "2024-01-01",
        periodEnd = "2024-03-01",
        skillTags = listOf("Kotlin", "Spring"),
    )

    @Test
    fun editModeLoadsExistingAndAutoExpandsOptionalWhenDetailPresent() = runTest(dispatcher) {
        val api = FakeExperienceApi(getOneResult = ApiResult.Success(existingWithDetail()))
        val vm = ExperienceEditViewModel(api, experienceId = "e-99")
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isEditMode)
        assertEquals("기존 경험", state.title)
        assertEquals(ExperienceType.JOB, state.type)
        assertEquals("기존 본문", state.body)
        assertEquals("상황", state.situation)
        assertEquals(listOf("Kotlin", "Spring"), state.skillTags)
        // detail이 있으므로 선택 항목이 자동으로 펼쳐져야 한다.
        assertTrue(state.optionalExpanded)
    }

    @Test
    fun editModeSaveSendsPatchToSameId() = runTest(dispatcher) {
        val api = FakeExperienceApi(getOneResult = ApiResult.Success(existingWithDetail()))
        val vm = ExperienceEditViewModel(api, experienceId = "e-99")
        testScheduler.advanceUntilIdle()

        vm.onTitleChange("수정된 제목")
        vm.save()
        testScheduler.advanceUntilIdle()

        val update = api.lastUpdate
        assertNotNull(update)
        assertEquals("e-99", update.first)
        assertEquals("수정된 제목", update.second.title)
        // 신규 생성 경로는 호출되지 않아야 한다.
        assertNull(api.lastCreate)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun editModeLoadFailureSurfacesLoadError() = runTest(dispatcher) {
        val api = FakeExperienceApi(getOneResult = ApiResult.Failure("불러오기 실패"))
        val vm = ExperienceEditViewModel(api, experienceId = "e-99")
        testScheduler.advanceUntilIdle()

        assertEquals("불러오기 실패", vm.state.value.loadError)
    }

    // UX-4: 로드 실패 후 retryLoad가 같은 id를 다시 불러와 복구한다.
    @Test
    fun retryLoadReloadsAndClearsLoadErrorOnSuccess() = runTest(dispatcher) {
        val api = FakeExperienceApi(getOneResult = ApiResult.Failure("불러오기 실패"))
        val vm = ExperienceEditViewModel(api, experienceId = "e-99")
        testScheduler.advanceUntilIdle()
        assertEquals("불러오기 실패", vm.state.value.loadError)

        // 다음 시도는 성공하도록 fake 응답을 교체.
        api.getOneResult = ApiResult.Success(existingWithDetail())
        vm.retryLoad()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.loadError)
        assertEquals("기존 경험", vm.state.value.title)
    }
}
