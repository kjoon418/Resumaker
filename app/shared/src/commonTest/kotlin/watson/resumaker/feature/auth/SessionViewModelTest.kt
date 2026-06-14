package watson.resumaker.feature.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeAccountApi
import watson.resumaker.fake.FakeSessionStore
import watson.resumaker.network.ApiResult
import watson.resumaker.model.dto.SignUpResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun signUpWithInvalidEmailShowsInlineErrorAndDoesNotCallApi() = runTest(dispatcher) {
        val api = FakeAccountApi()
        val session = FakeSessionStore()
        val vm = SessionViewModel(api, session)

        vm.onEmailChange("invalid")
        vm.onPasswordChange("12345678")
        vm.submit()

        assertNotNull(vm.state.value.emailError)
        assertNull(api.lastSignUp)
    }

    @Test
    fun signUpWithShortPasswordShowsError() = runTest(dispatcher) {
        val vm = SessionViewModel(FakeAccountApi(), FakeSessionStore())
        vm.onEmailChange("name@example.com")
        vm.onPasswordChange("123")
        vm.submit()

        assertNotNull(vm.state.value.passwordError)
    }

    @Test
    fun successfulSignUpIssuesUserIdAndDefersEntryUntilAcknowledged() = runTest(dispatcher) {
        val api = FakeAccountApi(signUpResult = ApiResult.Success(SignUpResponse("user-42")))
        val session = FakeSessionStore()
        val vm = SessionViewModel(api, session)

        vm.onEmailChange("name@example.com")
        vm.onPasswordChange("password1")
        vm.submit()
        testScheduler.advanceUntilIdle()

        // P0-1: 가입 성공 시 세션은 저장되지만, 보관 고지 화면을 거치기 전에는 홈으로 보내지 않는다.
        assertEquals("user-42", vm.state.value.issuedUserId)
        assertNull(vm.state.value.authenticatedUserId)
        assertEquals("user-42", session.currentUserId())
        assertEquals("name@example.com", session.currentEmail())

        // 사용자가 userId 보관을 확인하면 진입.
        vm.acknowledgeUserId()
        assertEquals("user-42", vm.state.value.authenticatedUserId)
    }

    @Test
    fun failedSignUpShowsSnackbarMessage() = runTest(dispatcher) {
        val api = FakeAccountApi(signUpResult = ApiResult.Failure("이미 가입된 이메일이에요."))
        val vm = SessionViewModel(api, FakeSessionStore())

        vm.onEmailChange("name@example.com")
        vm.onPasswordChange("password1")
        vm.submit()
        testScheduler.advanceUntilIdle()

        assertEquals("이미 가입된 이메일이에요.", vm.state.value.snackbarMessage)
        assertNull(vm.state.value.authenticatedUserId)
    }

    @Test
    fun reenterWithValidUserIdStoresSession() = runTest(dispatcher) {
        val session = FakeSessionStore()
        val vm = SessionViewModel(FakeAccountApi(), session)
        vm.selectMode(SessionMode.REENTER)
        vm.onUserIdChange("123e4567-e89b-12d3-a456-426614174000")
        vm.submit()

        assertEquals("123e4567-e89b-12d3-a456-426614174000", session.currentUserId())
        assertNotNull(vm.state.value.authenticatedUserId)
    }

    @Test
    fun reenterWithInvalidUserIdShowsError() = runTest(dispatcher) {
        val vm = SessionViewModel(FakeAccountApi(), FakeSessionStore())
        vm.selectMode(SessionMode.REENTER)
        vm.onUserIdChange("nope")
        vm.submit()

        assertNotNull(vm.state.value.userIdError)
        assertNull(vm.state.value.authenticatedUserId)
    }
}
