package watson.resumaker.feature.artifact

import kotlinx.coroutines.CompletableDeferred
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
import watson.resumaker.model.dto.ArtifactVersionsResponse
import watson.resumaker.model.dto.VersionHistoryResponse
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactVersionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun section(
        definitionKey: String,
        content: String,
        id: String = "s-$definitionKey",
        status: SectionStatus = SectionStatus.GENERATED,
    ) = ArtifactSectionResponse(
        id = id,
        sectionKind = SectionKind.CAREER,
        definitionKey = definitionKey,
        content = content,
        status = status,
        sourceExperienceIds = listOf("e-1"),
    )

    private fun version(
        versionId: String,
        active: Boolean,
        createdAt: String,
        sections: List<ArtifactSectionResponse>,
    ) = VersionHistoryResponse(
        versionId = versionId,
        active = active,
        createdAt = createdAt,
        sections = sections,
    )

    /** 두 버전(v-1 오래된, v-2 최신·활성)을 가진 기본 목록 응답. */
    private fun twoVersions(activeId: String = "v-2") = ArtifactVersionsResponse(
        artifactId = "a-1",
        kind = ArtifactKind.RESUME,
        activeVersionId = activeId,
        versions = listOf(
            version(
                "v-1", active = activeId == "v-1", createdAt = "2024-01-01T10:00:00",
                sections = listOf(section("career", "1차 경력")),
            ),
            version(
                "v-2", active = activeId == "v-2", createdAt = "2024-01-02T11:30:00",
                sections = listOf(section("career", "2차 경력")),
            ),
        ),
    )

    private fun restoredArtifact(activeVersionId: String) = ArtifactResponse(
        id = "a-1",
        kind = ArtifactKind.RESUME,
        activeVersion = ArtifactVersionResponse(
            versionId = activeVersionId,
            sections = listOf(section("career", "되돌린 경력")),
        ),
        prunedVersionCount = 0,
    )

    @Test
    fun loadMapsVersionsWithOrdinalsAndActive() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("a-1", api.getVersionsId)
        assertEquals(2, s.versions.size)
        // 생성순(오래된→최신)으로 1부터 번호가 매겨진다.
        assertEquals(1, s.versions[0].ordinal)
        assertEquals(2, s.versions[1].ordinal)
        assertEquals("버전 2", s.versions[1].label)
        // 활성 표시는 v-2.
        assertTrue(s.versions.single { it.versionId == "v-2" }.active)
        assertFalse(s.versions.single { it.versionId == "v-1" }.active)
        assertEquals("v-2", s.activeVersionId)
    }

    @Test
    fun defaultComparisonSelectsActiveVersusPrevious() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        // 기본 비교: 좌측=활성(v-2), 우측=활성 직전(v-1).
        assertEquals("v-2", s.leftVersionId)
        assertEquals("v-1", s.rightVersionId)
        assertTrue(s.canCompare)
    }

    @Test
    fun diffRowsMatchSameDefinitionKey() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val rows = vm.state.value.diffRows
        // 같은 definitionKey("career")가 한 줄로 대응되고, 좌(v-2)/우(v-1) 내용이 다르므로 changed.
        assertEquals(1, rows.size)
        assertEquals("career", rows.single().definitionKey)
        assertEquals("2차 경력", rows.single().left?.content)
        assertEquals("1차 경력", rows.single().right?.content)
        assertTrue(rows.single().changed)
    }

    @Test
    fun diffRowsHandleKeyMismatch() = runTest(dispatcher) {
        // v-1엔 career만, v-2엔 career+summary. 키 불일치 항목은 반대편이 null로 노출돼야 한다(누락 없음).
        val response = ArtifactVersionsResponse(
            artifactId = "a-1",
            kind = ArtifactKind.RESUME,
            activeVersionId = "v-2",
            versions = listOf(
                version(
                    "v-1", active = false, createdAt = "2024-01-01T10:00:00",
                    sections = listOf(section("career", "1차 경력")),
                ),
                version(
                    "v-2", active = true, createdAt = "2024-01-02T10:00:00",
                    sections = listOf(section("career", "2차 경력"), section("summary", "요약")),
                ),
            ),
        )
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(response))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        // 좌측=v-2(career, summary), 우측=v-1(career). 좌측 순서 기준으로 정렬.
        val rows = vm.state.value.diffRows
        assertEquals(2, rows.size)
        val career = rows.single { it.definitionKey == "career" }
        assertEquals("2차 경력", career.left?.content)
        assertEquals("1차 경력", career.right?.content)
        // summary는 좌측(v-2)에만 있고 우측(v-1)엔 없음 → right=null로 "이 버전엔 없음".
        val summary = rows.single { it.definitionKey == "summary" }
        assertEquals("요약", summary.left?.content)
        assertNull(summary.right)
        assertTrue(summary.changed)
    }

    @Test
    fun selectVersionsUpdatesComparison() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        vm.selectLeftVersion("v-1")
        vm.selectRightVersion("v-2")
        val s = vm.state.value
        assertEquals("v-1", s.leftVersionId)
        assertEquals("v-2", s.rightVersionId)
        // 방향이 바뀌어 좌측이 v-1("1차"), 우측이 v-2("2차").
        assertEquals("1차 경력", s.diffRows.single().left?.content)
        assertEquals("2차 경력", s.diffRows.single().right?.content)
    }

    @Test
    fun restoreSuccessReflectsActiveTransition() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        // v-1으로 복원 → 서버가 활성 v-1을 돌려준다.
        api.restoreResult = ApiResult.Success(restoredArtifact(activeVersionId = "v-1"))
        vm.restoreVersion("v-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("a-1" to "v-1", api.lastRestore)
        // 활성 표시가 v-1으로 전환되고, 기준 버전도 새 활성으로 맞춰진다.
        assertEquals("v-1", s.activeVersionId)
        assertTrue(s.versions.single { it.versionId == "v-1" }.active)
        assertFalse(s.versions.single { it.versionId == "v-2" }.active)
        assertEquals("v-1", s.leftVersionId)
        assertEquals("이 버전으로 되돌렸어요.", s.actionMessage)
        assertFalse(s.restoreInFlight)
    }

    @Test
    fun restoreActiveVersionIsNoOp() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        // 이미 활성인 v-2 복원은 의미가 없어 API를 호출하지 않는다.
        vm.restoreVersion("v-2")
        testScheduler.advanceUntilIdle()
        assertEquals(0, api.restoreCount)
    }

    @Test
    fun restoreNotFoundShowsMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        api.restoreResult = ApiResult.Failure(message = "되돌릴 버전을 찾을 수 없어요.", code = "NOT_FOUND")
        vm.restoreVersion("v-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("되돌릴 버전을 찾을 수 없어요.", s.actionMessage)
        // 실패 시 활성은 그대로 유지.
        assertEquals("v-2", s.activeVersionId)
        assertFalse(s.restoreInFlight)
    }

    @Test
    fun restoreInFlightGuardBlocksDuplicate() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        api.restoreGate = gate
        api.restoreResult = ApiResult.Success(restoredArtifact(activeVersionId = "v-1"))

        vm.restoreVersion("v-1")
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.restoreInFlight)

        // 진행 중 다른 복원 시도는 무시된다(중복 호출 차단).
        vm.restoreVersion("v-1")
        testScheduler.advanceUntilIdle()
        assertEquals(1, api.restoreCount)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.restoreInFlight)
    }

    @Test
    fun loadFailureSetsErrorMessage() = runTest(dispatcher) {
        val api = FakeArtifactApi(
            getVersionsResult = ApiResult.Failure(message = "산출물을 찾을 수 없어요.", code = "NOT_FOUND"),
        )
        val vm = ArtifactVersionsViewModel(api, artifactId = "missing")
        testScheduler.advanceUntilIdle()

        assertEquals("산출물을 찾을 수 없어요.", vm.state.value.errorMessage)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun singleVersionCannotCompare() = runTest(dispatcher) {
        val response = ArtifactVersionsResponse(
            artifactId = "a-1",
            kind = ArtifactKind.RESUME,
            activeVersionId = "v-1",
            versions = listOf(
                version(
                    "v-1", active = true, createdAt = "2024-01-01T10:00:00",
                    sections = listOf(section("career", "유일 버전")),
                ),
            ),
        )
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(response))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.canCompare)
        // 좌·우가 같은(유일) 버전으로 채워진다.
        assertEquals("v-1", s.leftVersionId)
        assertEquals("v-1", s.rightVersionId)
        assertNotNull(s.leftVersion)
    }

    @Test
    fun defaultComparisonPicksTruePredecessorWhenActiveIsMiddle() = runTest(dispatcher) {
        // 활성이 v-2(중간)인 경우 생성순 직전(v-1)이 우측 기본값이어야 한다.
        // filter.lastOrNull 이전 버그라면 목록 끝(v-3)을 골랐을 것임 — 직전 계산 수정 확인.
        val response = ArtifactVersionsResponse(
            artifactId = "a-1",
            kind = ArtifactKind.RESUME,
            activeVersionId = "v-2",
            versions = listOf(
                version("v-1", active = false, createdAt = "2024-01-01T10:00:00", sections = listOf(section("career", "v1"))),
                version("v-2", active = true,  createdAt = "2024-01-02T10:00:00", sections = listOf(section("career", "v2"))),
                version("v-3", active = false, createdAt = "2024-01-03T10:00:00", sections = listOf(section("career", "v3"))),
            ),
        )
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(response))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("v-2", s.leftVersionId)
        // 직전(v-1)이어야 한다. v-3이 선택되면 버그.
        assertEquals("v-1", s.rightVersionId)
    }

    @Test
    fun defaultComparisonUsesSuccessorWhenActiveIsFirst() = runTest(dispatcher) {
        // 복원으로 활성이 v-1(첫 번째)이 된 상황. 직전이 없으므로 직후(v-2)가 우측 기본값이어야 한다.
        val response = ArtifactVersionsResponse(
            artifactId = "a-1",
            kind = ArtifactKind.RESUME,
            activeVersionId = "v-1",
            versions = listOf(
                version("v-1", active = true,  createdAt = "2024-01-01T10:00:00", sections = listOf(section("career", "v1"))),
                version("v-2", active = false, createdAt = "2024-01-02T10:00:00", sections = listOf(section("career", "v2"))),
                version("v-3", active = false, createdAt = "2024-01-03T10:00:00", sections = listOf(section("career", "v3"))),
            ),
        )
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(response))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals("v-1", s.leftVersionId)
        // 직전이 없으므로 직후(v-2)가 선택돼야 한다. v-3이 선택되면 버그.
        assertEquals("v-2", s.rightVersionId)
    }

    @Test
    fun consumeActionMessageClearsIt() = runTest(dispatcher) {
        val api = FakeArtifactApi(getVersionsResult = ApiResult.Success(twoVersions()))
        val vm = ArtifactVersionsViewModel(api, artifactId = "a-1")
        testScheduler.advanceUntilIdle()

        api.restoreResult = ApiResult.Failure(message = "되돌릴 버전을 찾을 수 없어요.", code = "NOT_FOUND")
        vm.restoreVersion("v-1")
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.state.value.actionMessage)

        vm.consumeActionMessage()
        assertNull(vm.state.value.actionMessage)
    }
}
