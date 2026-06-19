package watson.resumaker.feature.mypage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeAccountApi
import watson.resumaker.fake.FakeSessionStore
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialStateReadsEmailAndUserIdFromSession() = runTest(dispatcher) {
        val session = FakeSessionStore(userId = "u-1", email = "me@example.com")
        val vm = MyPageViewModel(FakeAccountApi(), session)

        assertEquals("me@example.com", vm.state.value.email)
        assertEquals("u-1", vm.state.value.userId)
    }

    @Test
    fun logoutRequiresConfirmationBeforeClearingSession() = runTest(dispatcher) {
        val session = FakeSessionStore(userId = "u-1", email = "me@example.com")
        val vm = MyPageViewModel(FakeAccountApi(), session)

        // P0-2: 요청만으로는 세션을 지우지 않는다(확인 다이얼로그).
        vm.requestLogout()
        assertTrue(vm.state.value.confirmingLogout)
        assertFalse(session.cleared)
        assertFalse(vm.state.value.signedOut)

        // 취소하면 세션 유지.
        vm.cancelLogout()
        assertFalse(vm.state.value.confirmingLogout)
        assertFalse(session.cleared)

        // 확정해야 세션 클리어 + signedOut.
        vm.requestLogout()
        vm.confirmLogout()
        assertTrue(session.cleared)
        assertTrue(vm.state.value.signedOut)
    }

    @Test
    fun confirmLogoutClearsLocallyAndCallsServerLogout() = runTest(dispatcher) {
        val session = FakeSessionStore(userId = "u-1", email = "me@example.com")
        val api = FakeAccountApi()
        val vm = MyPageViewModel(api, session)

        vm.requestLogout()
        vm.confirmLogout()
        // 로컬은 즉시 로그아웃된다.
        assertTrue(session.cleared)
        assertTrue(vm.state.value.signedOut)

        // 서버 로그아웃은 best-effort로 뒤이어 호출된다(쿠키·토큰 폐기).
        testScheduler.advanceUntilIdle()
        assertTrue(api.logoutCalled)
    }

    @Test
    fun deleteSuccessClearsSessionAndSignsOut() = runTest(dispatcher) {
        val session = FakeSessionStore(userId = "u-1", email = "me@example.com")
        val api = FakeAccountApi(deleteResult = ApiResult.Success(Unit))
        val vm = MyPageViewModel(api, session)

        vm.requestDelete()
        assertTrue(vm.state.value.confirmingDelete)
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertTrue(api.deleteCalled)
        assertTrue(session.cleared)
        assertTrue(vm.state.value.signedOut)
    }

    @Test
    fun deleteFailureKeepsSessionAndShowsSnackbar() = runTest(dispatcher) {
        val session = FakeSessionStore(userId = "u-1", email = "me@example.com")
        val api = FakeAccountApi(deleteResult = ApiResult.Failure("탈퇴에 실패했어요."))
        val vm = MyPageViewModel(api, session)

        vm.requestDelete()
        vm.confirmDelete()
        testScheduler.advanceUntilIdle()

        assertFalse(session.cleared)
        assertFalse(vm.state.value.signedOut)
        assertEquals("탈퇴에 실패했어요.", vm.state.value.snackbarMessage)
    }
}
