package watson.resumaker.feature.experience

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ExperienceApi

/**
 * 경험 목록 화면 UiState.
 */
data class ExperienceListUiState(
    val loading: Boolean = true,
    val items: List<ExperienceResponse> = emptyList(),
    val errorMessage: String? = null,
    /** 삭제 확인 대상(다이얼로그 표시). null이면 닫힘. */
    val pendingDelete: ExperienceResponse? = null,
    val snackbarMessage: String? = null,
)

/**
 * 경험 목록 ViewModel: GET /experiences 로드, 삭제(DELETE) 처리.
 */
class ExperienceListViewModel(
    private val experienceApi: ExperienceApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ExperienceListUiState())
    val state: StateFlow<ExperienceListUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = experienceApi.getAll()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, items = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, errorMessage = result.message) }
            }
        }
    }

    fun requestDelete(item: ExperienceResponse) = _state.update { it.copy(pendingDelete = item) }
    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }
    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            when (val result = experienceApi.delete(target.id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = it.items.filterNot { e -> e.id == target.id },
                        snackbarMessage = "삭제했어요.",
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(snackbarMessage = result.message) }
            }
        }
    }
}
