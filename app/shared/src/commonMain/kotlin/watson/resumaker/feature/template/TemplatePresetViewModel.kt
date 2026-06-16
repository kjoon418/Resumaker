package watson.resumaker.feature.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.TemplatePresetResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.TemplatePresetApi

/**
 * 프리셋 선택 화면 UiState(FU-B).
 * [loading]: 프리셋 목록 로딩 중.
 * [presets]: 로드된 프리셋 목록.
 * [errorMessage]: 로드 실패 메시지.
 * [selectedPreset]: 사용자가 고른 프리셋. non-null이면 편집 화면으로 이동한다.
 */
data class TemplatePresetUiState(
    val loading: Boolean = false,
    val presets: List<TemplatePresetResponse> = emptyList(),
    val errorMessage: String? = null,
    val selectedPreset: TemplatePresetResponse? = null,
)

/**
 * 프리셋 선택 ViewModel(FU-B, 도메인 이해 §2.5 "프리셋 선택").
 *
 * 프리셋 목록을 불러오고 사용자가 하나를 고르면 [selectedPreset]에 담아 화면 전환을 신호한다.
 * 선택된 프리셋의 이름·섹션은 TemplateEditViewModel 초기값으로 전달한다.
 */
class TemplatePresetViewModel(
    private val presetApi: TemplatePresetApi,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplatePresetUiState())
    val state: StateFlow<TemplatePresetUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = presetApi.getAll()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, presets = result.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, errorMessage = result.message) }
            }
        }
    }

    fun selectPreset(preset: TemplatePresetResponse) {
        _state.update { it.copy(selectedPreset = preset) }
    }

    /** 편집 화면으로 이동한 뒤 [selectedPreset]을 소비해 재진입을 막는다. */
    fun consumeSelectedPreset() {
        _state.update { it.copy(selectedPreset = null) }
    }
}
