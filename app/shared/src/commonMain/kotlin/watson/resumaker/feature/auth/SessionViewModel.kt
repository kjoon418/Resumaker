package watson.resumaker.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.LoginRequest
import watson.resumaker.model.dto.SignUpRequest
import watson.resumaker.network.AccountApi
import watson.resumaker.network.ApiResult
import watson.resumaker.session.SessionStore
import watson.resumaker.validation.Validators

/** 세션 진입 모드(§8.1 세그먼트). */
enum class SessionMode { SIGN_UP, LOGIN }

/**
 * 세션 진입 화면 UiState. 불변 데이터 클래스로 단방향 흐름을 유지한다.
 */
data class SessionUiState(
    val mode: SessionMode = SessionMode.SIGN_UP,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    /**
     * 폼 하단에 지속적으로 보여주는 인라인 오류(특정 필드에 귀속되지 않는 실패). 로그인 실패는 계정 열거 방지를 위해
     * 이메일/비번을 구분하지 않으므로 여기로 모은다 — 사라지는 토스트 대신 머무는 메시지로 보여준다(UX-09).
     */
    val formError: String? = null,
    val submitting: Boolean = false,
    val snackbarMessage: String? = null,
    /** 사용자가 진입을 확정한 userId(상위가 관찰해 홈으로 이동). */
    val authenticatedUserId: String? = null,
)

/**
 * 세션 진입 ViewModel: 회원가입(POST /auth/signup) + 로그인(POST /auth/login).
 * 입력 검증은 [Validators]로 즉시 수행하고, 제출 시 서버 결과를 UiState에 반영한다.
 *
 * 로그인이 도입되어 가입 후 userId 암기를 강요할 필요가 없으므로, 가입 성공 시 곧장 홈으로 진입한다.
 */
class SessionViewModel(
    private val accountApi: AccountApi,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    fun selectMode(mode: SessionMode) =
        _state.update { it.copy(mode = mode, emailError = null, passwordError = null, formError = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, formError = null) }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    /** 가입 또는 로그인 제출(현재 모드 기준). */
    fun submit() {
        when (_state.value.mode) {
            SessionMode.SIGN_UP -> signUp()
            SessionMode.LOGIN -> login()
        }
    }

    private fun signUp() {
        val current = _state.value
        val emailError = Validators.validateEmail(current.email)
        val passwordError = Validators.validatePassword(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = accountApi.signUp(
                SignUpRequest(email = current.email.trim(), password = current.password),
            )
            when (result) {
                is ApiResult.Success -> {
                    val userId = result.value.userId
                    session.save(userId = userId, email = current.email.trim())
                    // 로그인이 있으므로 가입 성공 시 곧장 홈으로 진입한다(복구 코드 고지 불필요).
                    _state.update { it.copy(submitting = false, authenticatedUserId = userId) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        emailError = if (result.field == "email") result.message else it.emailError,
                        passwordError = if (result.field == "password") result.message else it.passwordError,
                        snackbarMessage = if (result.field == null) result.message else null,
                    )
                }
            }
        }
    }

    private fun login() {
        val current = _state.value
        val emailError = Validators.validateEmail(current.email)
        val passwordError = Validators.validatePassword(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = accountApi.login(
                LoginRequest(email = current.email.trim(), password = current.password),
            )
            when (result) {
                is ApiResult.Success -> {
                    val userId = result.value.userId
                    session.save(userId = userId, email = current.email.trim())
                    _state.update { it.copy(submitting = false, authenticatedUserId = userId) }
                }
                is ApiResult.Failure -> _state.update {
                    // 계정 열거 방지를 위해 서버는 이메일/비번을 구분하지 않는 일반 메시지를 준다. 사라지는 토스트가
                    // 아니라 폼에 머무는 인라인 메시지로 노출한다(UX-09 — 가입 실패와 동일하게 지속 표시).
                    it.copy(submitting = false, formError = result.message)
                }
            }
        }
    }
}
