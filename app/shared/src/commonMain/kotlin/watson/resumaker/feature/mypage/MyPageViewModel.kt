package watson.resumaker.feature.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.network.AccountApi
import watson.resumaker.network.ApiResult
import watson.resumaker.session.SessionStore

data class MyPageUiState(
    val email: String? = null,
    val userId: String? = null,
    /** 로그아웃 확인 다이얼로그 표시 여부(P0-2: userId 소실 경고). */
    val confirmingLogout: Boolean = false,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val snackbarMessage: String? = null,
    /** 로그아웃/탈퇴 완료 시 true(상위가 세션 화면으로 리셋). */
    val signedOut: Boolean = false,
)

/**
 * 마이페이지 ViewModel: 이메일·userId 표시, 로그아웃(세션 클리어), 회원 탈퇴(DELETE /me).
 */
class MyPageViewModel(
    private val accountApi: AccountApi,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MyPageUiState(email = session.currentEmail(), userId = session.currentUserId()),
    )
    val state: StateFlow<MyPageUiState> = _state.asStateFlow()

    fun requestDelete() = _state.update { it.copy(confirmingDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }
    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    /** 로그아웃 요청 → 먼저 userId 소실 경고 다이얼로그를 띄운다(P0-2). */
    fun requestLogout() = _state.update { it.copy(confirmingLogout = true) }
    fun cancelLogout() = _state.update { it.copy(confirmingLogout = false) }

    /**
     * 로그아웃 확정: 로컬 세션을 즉시 비워 곧장 진입 화면으로 보내고(즉각 UX), 서버에는 best-effort로 로그아웃을
     * 요청해 쿠키·토큰을 폐기한다(실패해도 로컬은 이미 로그아웃). 확인 다이얼로그 이후에만 호출된다.
     */
    fun confirmLogout() {
        session.clear()
        _state.update { it.copy(confirmingLogout = false, signedOut = true) }
        viewModelScope.launch { accountApi.logout() }
    }

    /** 회원 탈퇴: DELETE /me 성공 시 세션 클리어 후 진입 화면으로. */
    fun confirmDelete() {
        _state.update { it.copy(confirmingDelete = false, deleting = true) }
        viewModelScope.launch {
            when (val result = accountApi.deleteAccount()) {
                is ApiResult.Success -> {
                    session.clear()
                    _state.update { it.copy(deleting = false, signedOut = true) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, snackbarMessage = result.message)
                }
            }
        }
    }
}
