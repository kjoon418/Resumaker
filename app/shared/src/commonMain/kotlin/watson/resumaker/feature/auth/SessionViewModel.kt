package watson.resumaker.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.SignUpRequest
import watson.resumaker.network.AccountApi
import watson.resumaker.network.ApiResult
import watson.resumaker.session.SessionStore
import watson.resumaker.validation.Validators

/** 세션 진입 모드(§8.1 세그먼트). */
enum class SessionMode { SIGN_UP, REENTER }

/**
 * 세션 진입 화면 UiState. 불변 데이터 클래스로 단방향 흐름을 유지한다.
 */
data class SessionUiState(
    val mode: SessionMode = SessionMode.SIGN_UP,
    val email: String = "",
    val password: String = "",
    val userIdInput: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val userIdError: String? = null,
    val submitting: Boolean = false,
    val snackbarMessage: String? = null,
    /**
     * 가입 성공 직후 발급된 userId. null이 아니면 "재로그인 열쇠 고지" 화면을 띄운다(도메인 §62·§275, 수용기준 14).
     * 사용자가 보관을 확인([acknowledgeUserId])하기 전까지는 홈으로 보내지 않는다.
     */
    val issuedUserId: String? = null,
    /** 사용자가 진입을 확정한 userId(상위가 관찰해 홈으로 이동). */
    val authenticatedUserId: String? = null,
)

/**
 * 세션 진입 ViewModel: 회원가입(POST /auth/signup) + userId 재진입.
 * 입력 검증은 [Validators]로 즉시 수행하고, 제출 시 서버 결과를 UiState에 반영한다.
 */
class SessionViewModel(
    private val accountApi: AccountApi,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    fun selectMode(mode: SessionMode) =
        _state.update { it.copy(mode = mode, emailError = null, passwordError = null, userIdError = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null) }
    fun onUserIdChange(value: String) = _state.update { it.copy(userIdInput = value, userIdError = null) }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    /** 사용자가 발급된 userId 보관을 확인 → 홈으로 진입(P0-1 고지 화면의 "시작하기"). */
    fun acknowledgeUserId() = _state.update { it.copy(authenticatedUserId = it.issuedUserId) }

    /** 가입 또는 재진입 제출(현재 모드 기준). */
    fun submit() {
        when (_state.value.mode) {
            SessionMode.SIGN_UP -> signUp()
            SessionMode.REENTER -> reenter()
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
                    // 곧장 홈으로 보내지 않고 userId 보관을 먼저 고지한다(P0-1).
                    _state.update { it.copy(submitting = false, issuedUserId = userId) }
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

    private fun reenter() {
        val current = _state.value
        val userIdError = Validators.validateUserId(current.userIdInput)
        if (userIdError != null) {
            _state.update { it.copy(userIdError = userIdError) }
            return
        }
        // 재진입은 별도 로그인 API가 없으므로 입력 userId를 그대로 세션에 보관한다(브리프 §API).
        val userId = current.userIdInput.trim()
        session.save(userId = userId, email = null)
        _state.update { it.copy(authenticatedUserId = userId) }
    }
}
