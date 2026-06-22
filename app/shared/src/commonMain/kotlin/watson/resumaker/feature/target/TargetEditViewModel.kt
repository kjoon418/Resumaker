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
import watson.resumaker.model.type.StrategyStatus
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
    /**
     * 편집 진입 시 로드된 기존 전략 상태. recruitDirection을 수정하면 백그라운드에서 전략이 재추출되므로,
     * 전략이 이미 있던(READY) 목표에는 수정 InfoCard·재분석 안내 스낵바를 보여준다.
     */
    val loadedStrategyStatus: StrategyStatus? = null,
) {
    val isEditMode: Boolean get() = editingId != null

    /** 채용 방향 수정 시 전략 재분석 안내(InfoCard) 노출 여부 — 편집 모드이면서 기존 전략이 준비된 경우만. */
    val showStrategyReanalyzeInfo: Boolean get() = isEditMode && loadedStrategyStatus == StrategyStatus.READY

    /** 저장 후 목록에서 노출할 성공 스낵바 문구(WX-4/16). 생성·수정(전략 있던 경우)을 구분한다. */
    val savedMessage: String
        get() = when {
            !isEditMode -> "목표를 추가했어요. 작성 전략을 분석하는 중이에요."
            showStrategyReanalyzeInfo -> "목표를 수정했어요. 변경된 내용으로 전략을 다시 분석하는 중이에요."
            else -> "목표를 수정했어요."
        }
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

    /** UX-4: 로드 에러 후 "다시 시도" — 같은 id를 다시 불러온다(편집 모드일 때만 의미 있음). */
    fun retryLoad() {
        val id = _state.value.editingId ?: return
        _state.update { it.copy(loadError = null) }
        loadExisting(id)
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
                            loadedStrategyStatus = t.strategyStatus,
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
