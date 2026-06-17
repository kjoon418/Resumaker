package watson.resumaker.feature.artifact

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeArtifactApi
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.ArtifactSectionResponse
import watson.resumaker.model.dto.ArtifactVersionResponse
import watson.resumaker.model.dto.GeneratedSectionResponse
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.SectionKind
import watson.resumaker.model.type.SectionStatus
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewSection(id: String, status: SectionStatus, content: String = "내용 $id") =
        ArtifactSectionResponse(
            id = id,
            sectionKind = SectionKind.CAREER,
            definitionKey = "career",
            content = content,
            status = status,
            sourceExperienceIds = listOf("e-1"),
        )

    @Test
    fun loadSuccessMapsSections() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Success(
                ArtifactResponse(
                    id = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersion = ArtifactVersionResponse(
                        versionId = "v-1",
                        sections = listOf(viewSection("s-1", SectionStatus.GENERATED)),
                    ),
                ),
            ),
        )
        val vm = ArtifactViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(1, vm.state.value.sections.size)
        assertEquals("a-1", api.getArtifactId)
        assertFalse(vm.state.value.hasFailedSections)
    }

    @Test
    fun failedSectionsAreFlaggedAndExcludedFromCopy() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Success(
                ArtifactResponse(
                    id = "a-1",
                    kind = ArtifactKind.RESUME,
                    activeVersion = ArtifactVersionResponse(
                        versionId = "v-1",
                        sections = listOf(
                            viewSection("ok", SectionStatus.GENERATED, content = "정상 내용"),
                            viewSection("bad", SectionStatus.GENERATION_FAILED, content = "쓰레기"),
                        ),
                    ),
                ),
            ),
        )
        val vm = ArtifactViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.hasFailedSections)
        // 전체 복사 텍스트는 정상 항목만 포함(가짜 성공 금지).
        assertEquals("정상 내용", vm.state.value.fullCopyText)
        assertTrue(vm.state.value.hasCopyableContent)
    }

    @Test
    fun notFoundSetsErrorMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getArtifactResult = ApiResult.Failure(message = "산출물을 찾을 수 없어요.", code = "404"),
        )
        val vm = ArtifactViewModel(api, artifactId = "missing")
        testScheduler.advanceUntilIdle()

        assertEquals("산출물을 찾을 수 없어요.", vm.state.value.errorMessage)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun initialResponseAvoidsFetch() = runTest(dispatcher) {
        val api = FakeArtifactApi() // getArtifact는 호출되면 실패(no result)지만 호출되지 않아야 한다.
        val initial = GenerationResponse(
            artifactId = "a-1",
            kind = ArtifactKind.PORTFOLIO,
            activeVersionId = "v-1",
            sections = listOf(
                GeneratedSectionResponse(
                    sectionId = "s-1",
                    definitionKey = "narrative",
                    sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
                    content = "서사",
                    status = SectionStatus.GENERATED,
                    sourceExperienceIds = listOf("e-1"),
                    factGroundings = emptyList(),
                ),
            ),
        )
        val vm = ArtifactViewModel(api, artifactId = "a-1", initial = initial)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals(1, vm.state.value.sections.size)
        assertEquals(ArtifactKind.PORTFOLIO, vm.state.value.kind)
        // 재조회하지 않았으므로 getArtifact 미호출.
        assertEquals(null, api.getArtifactId)
        assertNotNull(vm.state.value.sections.firstOrNull())
    }
}
