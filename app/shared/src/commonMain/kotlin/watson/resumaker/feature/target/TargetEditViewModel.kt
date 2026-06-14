package watson.resumaker.feature.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.CreateTargetRequest
import watson.resumaker.model.dto.UpdateTargetRequest
import watson.resumaker.network.ApiResult
import watson.resumaker.network.TargetApi
import watson.resumaker.validation.Validators

data class TargetEditUiState(
    val editingId: String? = null,
    val loading: Boolean = false,
    val companyName: String = "",
    val jobTitle: String = "",
    val recruitDirection: String = "",
    val recruitDirectionError: String? = null,
    val submitting: Boolean = false,
    val loadError: String? = null,
    val snackbarMessage: String? = null,
    val saved: Boolean = false,
) {
    val isEditMode: Boolean get() = editingId != null
}

/**
 * 목표 생성·수정 ViewModel. recruitDirection 필수 + companyName·jobTitle 선택.
 */
class TargetEditViewModel(
    private val targetApi: TargetApi,
    targetId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(TargetEditUiState(editingId = targetId))
    val state: StateFlow<TargetEditUiState> = _state.asStateFlow()

    init {
        if (targetId != null) loadExisting(targetId)
    }

    private fun loadExisting(id: String) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = targetApi.getOne(id)) {
                is ApiResult.Success -> {
                    val t = result.value
                    _state.update {
                        it.copy(
                            loading = false,
                            companyName = t.companyName.orEmpty(),
                            jobTitle = t.jobTitle.orEmpty(),
                            recruitDirection = t.recruitDirection,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, loadError = result.message) }
            }
        }
    }

    fun onCompanyChange(value: String) = _state.update { it.copy(companyName = value) }
    fun onJobTitleChange(value: String) = _state.update { it.copy(jobTitle = value) }
    fun onRecruitDirectionChange(value: String) =
        _state.update { it.copy(recruitDirection = value, recruitDirectionError = null) }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun save() {
        val current = _state.value
        val error = Validators.validateRequired(
            current.recruitDirection,
            "어떤 회사·직무를 겨냥하는지 알려주세요. 공고 내용을 붙여넣어도 좋아요.",
        )
        if (error != null) {
            _state.update { it.copy(recruitDirectionError = error) }
            return
        }

        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = if (current.isEditMode) {
                targetApi.update(
                    current.editingId!!,
                    UpdateTargetRequest(
                        recruitDirection = current.recruitDirection.trim(),
                        companyName = current.companyName.ifBlank { null },
                        jobTitle = current.jobTitle.ifBlank { null },
                    ),
                )
            } else {
                targetApi.create(
                    CreateTargetRequest(
                        recruitDirection = current.recruitDirection.trim(),
                        companyName = current.companyName.ifBlank { null },
                        jobTitle = current.jobTitle.ifBlank { null },
                    ),
                )
            }
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, saved = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        recruitDirectionError = if (result.field == "recruitDirection") result.message else it.recruitDirectionError,
                        snackbarMessage = if (result.field == null) result.message else null,
                    )
                }
            }
        }
    }
}
