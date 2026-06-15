package watson.resumaker.feature.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import watson.resumaker.fake.FakeAccountApi
import watson.resumaker.fake.FakeSessionStore
import watson.resumaker.model.dto.LoginResponse
import watson.resumaker.model.dto.SignUpResponse
import watson.resumaker.network.ApiResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun successfulSignUpStoresSessionAndEntersHome() = runTest(dispatcher) {
        val api = FakeAccountApi(signUpResult = ApiResult.Success(SignUpResponse("user-42")))
        val session = FakeSessionStore()
        val vm = SessionViewModel(api, session)

        vm.onEmailChange("name@example.com")
        vm.onPasswordChange("password1")
        vm.submit()
        testScheduler.advanceUntilIdle()

        // 로그인이 있으므로 가입 성공 시 곧장 홈으로 진입한다(복구 코드 고지 없음).
        assertEquals("user-42", vm.state.value.authenticatedUserId)
        assertEquals("user-42", session.currentUserId())
        assertEquals("name@example.com", session.currentEmail())
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
    fun successfulLoginStoresSessionAndAuthenticates() = runTest(dispatcher) {
        val api = FakeAccountApi(loginResult = ApiResult.Success(LoginResponse("user-7")))
        val session = FakeSessionStore()
        val vm = SessionViewModel(api, session)

        vm.selectMode(SessionMode.LOGIN)
        vm.onEmailChange("name@example.com")
        vm.onPasswordChange("password1")
        vm.submit()
        testScheduler.advanceUntilIdle()

        assertEquals("user-7", vm.state.value.authenticatedUserId)
        assertEquals("user-7", session.currentUserId())
        assertEquals("name@example.com", session.currentEmail())
        assertEquals("name@example.com", api.lastLogin?.email)
    }

    @Test
    fun failedLoginShowsGenericSnackbarAndDoesNotAuthenticate() = runTest(dispatcher) {
        val api = FakeAccountApi(loginResult = ApiResult.Failure("이메일 또는 비밀번호가 일치하지 않아요."))
        val session = FakeSessionStore()
        val vm = SessionViewModel(api, session)

        vm.selectMode(SessionMode.LOGIN)
        vm.onEmailChange("name@example.com")
        vm.onPasswordChange("wrongpass")
        vm.submit()
        testScheduler.advanceUntilIdle()

        assertEquals("이메일 또는 비밀번호가 일치하지 않아요.", vm.state.value.snackbarMessage)
        assertNull(vm.state.value.authenticatedUserId)
        assertNull(session.currentUserId())
    }

    @Test
    fun loginWithInvalidInputShowsInlineErrorAndDoesNotCallApi() = runTest(dispatcher) {
        val api = FakeAccountApi()
        val vm = SessionViewModel(api, FakeSessionStore())

        vm.selectMode(SessionMode.LOGIN)
        vm.onEmailChange("invalid")
        vm.onPasswordChange("123")
        vm.submit()

        assertNotNull(vm.state.value.emailError)
        assertNotNull(vm.state.value.passwordError)
        assertNull(api.lastLogin)
        assertNull(vm.state.value.authenticatedUserId)
    }
}
