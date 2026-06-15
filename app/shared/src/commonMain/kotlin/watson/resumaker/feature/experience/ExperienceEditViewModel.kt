package watson.resumaker.feature.experience

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.CreateExperienceRequest
import watson.resumaker.model.dto.ExperienceDetailRequest
import watson.resumaker.model.dto.UpdateExperienceRequest
import watson.resumaker.model.type.ExperienceType
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ExperienceApi
import watson.resumaker.validation.Validators

/**
 * 경험 생성·수정 화면 UiState. 필수(제목·유형·본문) + 선택(STAR·기간·역량).
 */
data class ExperienceEditUiState(
    val editingId: String? = null,
    val loading: Boolean = false,
    val title: String = "",
    val type: ExperienceType? = null,
    val body: String = "",
    val situation: String = "",
    val action: String = "",
    val result: String = "",
    val periodStart: String = "",
    val periodEnd: String = "",
    val skillTags: List<String> = emptyList(),
    val skillInput: String = "",
    val optionalExpanded: Boolean = false,
    val titleError: String? = null,
    val typeError: String? = null,
    val bodyError: String? = null,
    val submitting: Boolean = false,
    val loadError: String? = null,
    val snackbarMessage: String? = null,
    /** 저장 성공 시 true(상위가 목록으로 복귀). */
    val saved: Boolean = false,
) {
    val isEditMode: Boolean get() = editingId != null
}

/**
 * 경험 생성·수정 ViewModel.
 * 수정 모드면 GET /experiences/{id}로 선로딩, 저장 시 POST 또는 PATCH.
 * 클라이언트 검증은 도메인 카피와 동일한 메시지로 즉시 피드백한다(§8.4).
 */
class ExperienceEditViewModel(
    private val experienceApi: ExperienceApi,
    experienceId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(ExperienceEditUiState(editingId = experienceId))
    val state: StateFlow<ExperienceEditUiState> = _state.asStateFlow()

    init {
        if (experienceId != null) loadExisting(experienceId)
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
            when (val result = experienceApi.getOne(id)) {
                is ApiResult.Success -> {
                    val e = result.value
                    _state.update {
                        it.copy(
                            loading = false,
                            title = e.title,
                            type = e.type,
                            body = e.body,
                            situation = e.situation.orEmpty(),
                            action = e.action.orEmpty(),
                            result = e.result.orEmpty(),
                            periodStart = e.periodStart.orEmpty(),
                            periodEnd = e.periodEnd.orEmpty(),
                            skillTags = e.skillTags,
                            optionalExpanded = e.situation != null || e.action != null || e.result != null ||
                                e.periodStart != null || e.skillTags.isNotEmpty(),
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, loadError = result.message) }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, titleError = null) }
    fun onTypeChange(value: ExperienceType) = _state.update { it.copy(type = value, typeError = null) }
    fun onBodyChange(value: String) = _state.update { it.copy(body = value, bodyError = null) }
    fun onSituationChange(value: String) = _state.update { it.copy(situation = value) }
    fun onActionChange(value: String) = _state.update { it.copy(action = value) }
    fun onResultChange(value: String) = _state.update { it.copy(result = value) }
    fun onPeriodStartChange(value: String) = _state.update { it.copy(periodStart = value) }
    fun onPeriodEndChange(value: String) = _state.update { it.copy(periodEnd = value) }
    fun toggleOptional() = _state.update { it.copy(optionalExpanded = !it.optionalExpanded) }
    fun onSkillInputChange(value: String) = _state.update { it.copy(skillInput = value) }
    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    fun addSkill() {
        val tag = _state.value.skillInput.trim().removePrefix("#").trim()
        if (tag.isEmpty()) return
        _state.update {
            if (it.skillTags.any { existing -> existing.equals(tag, ignoreCase = true) }) {
                it.copy(skillInput = "")
            } else {
                it.copy(skillTags = it.skillTags + tag, skillInput = "")
            }
        }
    }

    fun removeSkill(tag: String) = _state.update { it.copy(skillTags = it.skillTags.filterNot { t -> t == tag }) }

    fun save() {
        val current = _state.value
        val titleError = Validators.validateRequired(current.title, "경험의 제목을 입력해 주세요.")
        val typeError = if (current.type == null) "경험 유형을 선택해 주세요." else null
        val bodyError = Validators.validateRequired(
            current.body,
            "이 경험에서 무슨 일을 했는지 한 줄이라도 적어 주세요. 나중에 더 자세히 보강할 수 있어요.",
        )
        if (titleError != null || typeError != null || bodyError != null) {
            _state.update {
                it.copy(titleError = titleError, typeError = typeError, bodyError = bodyError, optionalExpanded = it.optionalExpanded)
            }
            return
        }

        val detail = buildDetail(current)
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val result = if (current.isEditMode) {
                experienceApi.update(
                    current.editingId!!,
                    UpdateExperienceRequest(
                        title = current.title.trim(),
                        type = current.type!!,
                        body = current.body.trim(),
                        detail = detail,
                    ),
                )
            } else {
                experienceApi.create(
                    CreateExperienceRequest(
                        title = current.title.trim(),
                        type = current.type!!,
                        body = current.body.trim(),
                        detail = detail,
                    ),
                )
            }
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, saved = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        titleError = if (result.field == "title") result.message else it.titleError,
                        bodyError = if (result.field == "body") result.message else it.bodyError,
                        snackbarMessage = if (result.field == null) result.message else null,
                    )
                }
            }
        }
    }

    /** 선택 항목이 하나라도 채워졌을 때만 detail을 만든다(빈 detail 전송 회피). */
    private fun buildDetail(s: ExperienceEditUiState): ExperienceDetailRequest? {
        val hasAny = s.situation.isNotBlank() || s.action.isNotBlank() || s.result.isNotBlank() ||
            s.periodStart.isNotBlank() || s.periodEnd.isNotBlank() || s.skillTags.isNotEmpty()
        if (!hasAny) return null
        return ExperienceDetailRequest(
            situation = s.situation.ifBlank { null },
            action = s.action.ifBlank { null },
            result = s.result.ifBlank { null },
            periodStart = s.periodStart.ifBlank { null },
            periodEnd = s.periodEnd.ifBlank { null },
            skillTags = s.skillTags,
        )
    }

    companion object {
        /** §8.4 본문 정적 회상 보조 플레이스홀더(도메인 §96). */
        const val BODY_PLACEHOLDER =
            "예: 결제 시스템의 응답 지연을 분석해 캐시 전략을 도입했고, 평균 응답 시간을 40% 개선했습니다."

        /** §8.4 유도 질문 세트(도메인 §99 3문항). */
        val GUIDING_QUESTIONS = listOf(
            "내가 직접 한 일은 무엇이었나요?",
            "어떤 문제나 목표가 있었나요?",
            "결과적으로 무엇이 달라졌나요?",
        )
    }
}
