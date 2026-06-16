package watson.resumaker.feature.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.InterpretRequest
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.TemplateInterpretApi

/**
 * 붙여넣기 해석 확정 게이트 단계.
 * - [Idle]: 초기 상태(텍스트 입력 중).
 * - [Interpreting]: 해석 요청 중.
 * - [Gate]: 해석 결과 표시 — 사용자가 "이대로 만들기"(확정) 또는 폴백을 선택한다.
 *   [Gate.sections]는 읽기 전용(FU-C: 섹션 편집은 로드맵).
 * - [Fallback]: Unavailable이거나 빈 입력 → 폴백 안내.
 * - [Confirmed]: 사용자가 확정. [sections]와 [templateName]을 편집 화면으로 넘긴다.
 */
sealed interface InterpretGateState {
    data object Idle : InterpretGateState
    data object Interpreting : InterpretGateState
    data class Gate(val sections: List<SectionResponse>) : InterpretGateState
    data object Fallback : InterpretGateState
    data class Confirmed(val templateName: String, val sections: List<SectionResponse>) : InterpretGateState
}

data class TemplateInterpretUiState(
    val pastedText: String = "",
    val pastedTextError: String? = null,
    val templateName: String = "",
    val templateNameError: String? = null,
    val gate: InterpretGateState = InterpretGateState.Idle,
    val snackbarMessage: String? = null,
)

/**
 * 회사 양식 붙여넣기 + 확정 게이트 ViewModel(FU-C, 도메인 이해 §2.5).
 *
 * 흐름:
 * 1. 사용자가 텍스트를 붙여넣고 "해석" 실행.
 * 2. Interpreted → [InterpretGateState.Gate]: 추출된 섹션을 읽기 전용으로 표시.
 *    Unavailable / 빈 입력 → [InterpretGateState.Fallback]: 폴백 안내.
 * 3. 게이트에서 "이대로 만들기": 이름을 입력하고 확정 → [InterpretGateState.Confirmed].
 *    화면은 Confirmed를 감지해 [TemplateEditViewModel](presetName, presetSections)으로 이동한다.
 * 4. 폴백 버튼: [onFallbackToPreset]으로 프리셋 화면, [onFallbackToEdit]으로 빈 편집 화면.
 */
class TemplateInterpretViewModel(
    private val interpretApi: TemplateInterpretApi,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateInterpretUiState())
    val state: StateFlow<TemplateInterpretUiState> = _state.asStateFlow()

    fun onPastedTextChange(text: String) =
        _state.update { it.copy(pastedText = text, pastedTextError = null) }

    fun onTemplateNameChange(name: String) =
        _state.update { it.copy(templateName = name, templateNameError = null) }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    /**
     * 붙여넣기 텍스트 해석 실행.
     * 빈 입력은 해석 요청 없이 즉시 폴백으로 처리한다(도메인 이해 §2.5 "빈 입력·해석 불가 동일 폴백").
     */
    fun interpret() {
        val text = _state.value.pastedText.trim()
        if (text.isBlank()) {
            _state.update {
                it.copy(
                    pastedTextError = "양식 텍스트를 붙여넣어 주세요.",
                    gate = InterpretGateState.Fallback,
                )
            }
            return
        }

        _state.update { it.copy(gate = InterpretGateState.Interpreting) }
        viewModelScope.launch {
            when (val result = interpretApi.interpret(InterpretRequest(text = text))) {
                is ApiResult.Success -> {
                    val response = result.value
                    if (response.isInterpreted && response.sections.isNotEmpty()) {
                        _state.update { it.copy(gate = InterpretGateState.Gate(response.sections)) }
                    } else {
                        _state.update { it.copy(gate = InterpretGateState.Fallback) }
                    }
                }
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            gate = InterpretGateState.Fallback,
                            snackbarMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    /**
     * 게이트에서 "이대로 만들기" — 양식 이름을 검증하고 Confirmed 상태로 전환한다.
     * 화면이 Confirmed를 감지해 편집 화면(프리셋 모드)으로 이동한다.
     */
    fun confirmGate() {
        val current = _state.value
        val gateState = current.gate as? InterpretGateState.Gate ?: return
        val name = current.templateName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(templateNameError = "양식 이름을 적어 주세요. 예: 토스 백엔드 지원용") }
            return
        }
        _state.update { it.copy(gate = InterpretGateState.Confirmed(name, gateState.sections)) }
    }

    /** Confirmed 소비 — 편집 화면 이동 후 재진입 방지. */
    fun consumeConfirmed() {
        _state.update { it.copy(gate = InterpretGateState.Idle) }
    }

    /** 게이트·폴백에서 처음으로 돌아가 다시 붙여넣을 수 있게 한다. */
    fun resetToIdle() {
        _state.update { it.copy(gate = InterpretGateState.Idle, pastedTextError = null) }
    }
}
