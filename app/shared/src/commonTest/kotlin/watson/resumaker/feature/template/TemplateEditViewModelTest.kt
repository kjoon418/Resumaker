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
class TemplateEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun missingNameShowsErrorAndSkipsApi() = runTest(dispatcher) {
        val api = FakeTemplateApi()
        val vm = TemplateEditViewModel(api, templateId = null)
        // 섹션 이름은 채우되 양식 이름은 비운다.
        val key = vm.state.value.sections.first().key
        vm.onSectionNameChange(key, "요약")
        vm.save()

        assertNotNull(vm.state.value.nameError)
        assertNull(api.lastCreate)
    }

    @Test
    fun missingSectionNameShowsRowErrorAndSkipsApi() = runTest(dispatcher) {
        val api = FakeTemplateApi()
        val vm = TemplateEditViewModel(api, templateId = null)
        vm.onNameChange("내 양식")
        // 섹션 행 이름은 비운 상태로 저장.
        vm.save()

        assertNotNull(vm.state.value.sections.first().nameError)
        assertNull(api.lastCreate)
    }

    @Test
    fun validCreateSendsNameAndOrderedSections() = runTest(dispatcher) {
        val api = FakeTemplateApi()
        val vm = TemplateEditViewModel(api, templateId = null)
        vm.onNameChange("토스 백엔드 지원용")
        val firstKey = vm.state.value.sections.first().key
        vm.onSectionNameChange(firstKey, "한 줄 자기소개")
        vm.addSection()
        val secondKey = vm.state.value.sections[1].key
        vm.onSectionNameChange(secondKey, "주요 경력")
        vm.onSectionCharacterChange(secondKey, SectionCharacter.CAREER)
        vm.onSectionRequiredChange(secondKey, true)
        vm.save()
        testScheduler.advanceUntilIdle()

        val request = api.lastCreate
        assertNotNull(request)
        assertEquals("토스 백엔드 지원용", request.name)
        assertEquals(listOf("한 줄 자기소개", "주요 경력"), request.sections.map { it.name })
        assertEquals(SectionCharacter.CAREER, request.sections[1].character)
        assertTrue(request.sections[1].required)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun addAndRemoveSectionRespectsMinimumOne() = runTest(dispatcher) {
        val api = FakeTemplateApi()
        val vm = TemplateEditViewModel(api, templateId = null)
        val onlyKey = vm.state.value.sections.first().key

        // 마지막 한 행은 삭제되지 않는다(빈 양식 불성립).
        vm.removeSection(onlyKey)
        assertEquals(1, vm.state.value.sections.size)

        vm.addSection()
        assertEquals(2, vm.state.value.sections.size)
        val addedKey = vm.state.value.sections[1].key
        vm.removeSection(addedKey)
        assertEquals(1, vm.state.value.sections.size)
    }

    @Test
    fun moveSectionReordersRows() = runTest(dispatcher) {
        val api = FakeTemplateApi()
        val vm = TemplateEditViewModel(api, templateId = null)
        val firstKey = vm.state.value.sections.first().key
        vm.onSectionNameChange(firstKey, "A")
        vm.addSection()
        val secondKey = vm.state.value.sections[1].key
        vm.onSectionNameChange(secondKey, "B")

        vm.moveSectionDown(firstKey)
        assertEquals(listOf("B", "A"), vm.state.value.sections.map { it.name })

        vm.moveSectionUp(firstKey)
        assertEquals(listOf("A", "B"), vm.state.value.sections.map { it.name })
    }

    @Test
    fun loadExistingPopulatesNameAndSections() = runTest(dispatcher) {
        val api = FakeTemplateApi(
            getOneResult = ApiResult.Success(
                TemplateResponse(
                    id = "tpl-9",
                    name = "기존 양식",
                    sections = listOf(
                        SectionResponse("한 줄 자기소개", SectionCharacter.SUMMARY, false),
                        SectionResponse("주요 경력", SectionCharacter.CAREER, true),
                    ),
                ),
            ),
        )
        val vm = TemplateEditViewModel(api, templateId = "tpl-9")
        testScheduler.advanceUntilIdle()

        assertEquals("기존 양식", vm.state.value.name)
        assertEquals(listOf("한 줄 자기소개", "주요 경력"), vm.state.value.sections.map { it.name })
        assertEquals(SectionCharacter.CAREER, vm.state.value.sections[1].character)
        assertTrue(vm.state.value.sections[1].required)
    }

    // UX-4: 로드 실패 후 retryLoad가 같은 id를 다시 불러와 복구한다.
    @Test
    fun retryLoadReloadsAndClearsLoadErrorOnSuccess() = runTest(dispatcher) {
        val api = FakeTemplateApi(getOneResult = ApiResult.Failure("불러오기 실패"))
        val vm = TemplateEditViewModel(api, templateId = "tpl-99")
        testScheduler.advanceUntilIdle()
        assertEquals("불러오기 실패", vm.state.value.loadError)

        api.getOneResult = ApiResult.Success(
            TemplateResponse(id = "tpl-99", name = "복구된 양식", sections = listOf(SectionResponse("요약", SectionCharacter.SUMMARY, false))),
        )
        vm.retryLoad()
        testScheduler.advanceUntilIdle()

        assertNull(vm.state.value.loadError)
        assertEquals("복구된 양식", vm.state.value.name)
    }

    @Test
    fun editModeUpdatesExisting() = runTest(dispatcher) {
        val api = FakeTemplateApi(
            getOneResult = ApiResult.Success(
                TemplateResponse(id = "tpl-7", name = "원본", sections = listOf(SectionResponse("요약", SectionCharacter.SUMMARY, false))),
            ),
        )
        val vm = TemplateEditViewModel(api, templateId = "tpl-7")
        testScheduler.advanceUntilIdle()

        vm.onNameChange("수정본")
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals("tpl-7", api.lastUpdate?.first)
        assertEquals("수정본", api.lastUpdate?.second?.name)
        assertTrue(vm.state.value.saved)
    }
}
