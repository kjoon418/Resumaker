package watson.resumaker.feature.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.TargetApi

data class TargetListUiState(
    val loading: Boolean = true,
    val items: List<TargetResponse> = emptyList(),
    val errorMessage: String? = null,
    val pendingDelete: TargetResponse? = null,
    val snackbarMessage: String? = null,
    /** UX-5: 삭제 실패 시 "다시 시도"로 복구할 대상(스낵바 액션). */
    val retryableDelete: TargetResponse? = null,
)

/**
 * 목표 목록 ViewModel: GET /targets 로드, 삭제(DELETE) 처리.
 */
class TargetListViewModel(
    private val targetApi: TargetApi,
) : ViewModel() {

    private val _state = MutableStateFlow(TargetListUiState())
    val state: StateFlow<TargetListUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = targetApi.getAll()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, items = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, errorMessage = result.message) }
            }
        }
    }

    fun requestDelete(item: TargetResponse) = _state.update { it.copy(pendingDelete = item) }
    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }
    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    /** CQ-2: 스낵바 dismiss(액션 없이 닫힘) 시 스테일 retryableDelete 클리어. */
    fun clearRetryableDelete() = _state.update { it.copy(retryableDelete = null) }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        delete(target)
    }

    /** UX-5: 실패한 삭제를 그대로 다시 시도(스낵바 "다시 시도" 액션). */
    fun retryDelete() {
        val target = _state.value.retryableDelete ?: return
        delete(target)
    }

    private fun delete(target: TargetResponse) {
        viewModelScope.launch {
            when (val result = targetApi.delete(target.id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = it.items.filterNot { t -> t.id == target.id },
                        snackbarMessage = "삭제했어요.",
                        retryableDelete = null,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(snackbarMessage = result.message, retryableDelete = target)
                }
            }
        }
    }
}
