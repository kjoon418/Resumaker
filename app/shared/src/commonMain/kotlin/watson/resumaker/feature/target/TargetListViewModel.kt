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

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            when (val result = targetApi.delete(target.id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = it.items.filterNot { t -> t.id == target.id },
                        snackbarMessage = "삭제했어요.",
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(snackbarMessage = result.message) }
            }
        }
    }
}
