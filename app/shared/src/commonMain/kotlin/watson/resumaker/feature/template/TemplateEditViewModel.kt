package watson.resumaker.feature.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.CreateTemplateRequest
import watson.resumaker.model.dto.SectionRequest
import watson.resumaker.model.dto.UpdateTemplateRequest
import watson.resumaker.model.type.SectionCharacter
import watson.resumaker.network.ApiResult
import watson.resumaker.network.TemplateApi
import watson.resumaker.validation.Validators

/**
 * 편집 화면의 섹션 행. [key]는 목록 편집(추가·삭제·순서 이동)을 위한 안정적 클라이언트 식별자다(서버 미전송).
 * [nameError]는 행별 인라인 검증 메시지(이름 필수).
 */
data class SectionRow(
    val key: Int,
    val name: String = "",
    val character: SectionCharacter = SectionCharacter.SUMMARY,
    val required: Boolean = false,
    val nameError: String? = null,
)

data class TemplateEditUiState(
    val editingId: String? = null,
    val loading: Boolean = false,
    val name: String = "",
    val nameError: String? = null,
    val sections: List<SectionRow> = listOf(SectionRow(key = 0)),
    val sectionsError: String? = null,
    val submitting: Boolean = false,
    val loadError: String? = null,
    val snackbarMessage: String? = null,
    val saved: Boolean = false,
) {
    val isEditMode: Boolean get() = editingId != null
}

/**
 * 이력서 양식 생성·수정 ViewModel(FU-A/B).
 * - 신규 생성: [templateId] = null, [presetName]/[presetSections] = null → 빈 폼.
 * - 프리셋에서 시작(FU-B): [templateId] = null, [presetName]+[presetSections] non-null → 프리셋 값으로 미리 채운다.
 * - 수정: [templateId] non-null → 서버에서 기존 양식을 불러온다.
 *
 * TargetEditViewModel 패턴(UiState, ApiResult 분기, 에러 필드 매핑, 스낵바)을 미러한다.
 */
class TemplateEditViewModel(
    private val templateApi: TemplateApi,
    templateId: String?,
    presetName: String? = null,
    presetSections: List<SectionRow>? = null,
) : ViewModel() {

    /** 새 섹션 행에 부여할 단조 증가 키(목록 편집의 안정 식별자). */
    private var nextKey = 1

    private val initialSections: List<SectionRow> = presetSections
        ?: listOf(SectionRow(key = nextKey++))

    private val _state = MutableStateFlow(
        TemplateEditUiState(
            editingId = templateId,
            name = presetName ?: "",
            sections = initialSections,
        ),
    )
    val state: StateFlow<TemplateEditUiState> = _state.asStateFlow()

    init {
        if (templateId != null) loadExisting(templateId)
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
            when (val result = templateApi.getOne(id)) {
                is ApiResult.Success -> {
                    val t = result.value
                    val rows = t.sections.map { s ->
                        SectionRow(key = nextKey++, name = s.name, character = s.character, required = s.required)
                    }.ifEmpty { listOf(SectionRow(key = nextKey++)) }
                    _state.update {
                        it.copy(loading = false, name = t.name, sections = rows)
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, loadError = result.message) }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    // --- 섹션 행 편집 ---

    fun addSection() = _state.update {
        it.copy(sections = it.sections + SectionRow(key = nextKey++), sectionsError = null)
    }

    fun removeSection(key: Int) = _state.update {
        // 최소 1개 불변식: 마지막 한 행은 지우지 않는다(빈 양식 불성립).
        if (it.sections.size <= 1) it
        else it.copy(sections = it.sections.filterNot { row -> row.key == key })
    }

    fun moveSectionUp(key: Int) = _state.update { state ->
        val index = state.sections.indexOfFirst { it.key == key }
        if (index <= 0) state
        else state.copy(sections = state.sections.swapped(index, index - 1))
    }

    fun moveSectionDown(key: Int) = _state.update { state ->
        val index = state.sections.indexOfFirst { it.key == key }
        if (index < 0 || index >= state.sections.lastIndex) state
        else state.copy(sections = state.sections.swapped(index, index + 1))
    }

    fun onSectionNameChange(key: Int, value: String) = updateRow(key) { it.copy(name = value, nameError = null) }
    fun onSectionCharacterChange(key: Int, character: SectionCharacter) = updateRow(key) { it.copy(character = character) }
    fun onSectionRequiredChange(key: Int, required: Boolean) = updateRow(key) { it.copy(required = required) }

    private fun updateRow(key: Int, transform: (SectionRow) -> SectionRow) = _state.update { state ->
        state.copy(sections = state.sections.map { if (it.key == key) transform(it) else it })
    }

    private fun List<SectionRow>.swapped(a: Int, b: Int): List<SectionRow> =
        toMutableList().apply { val tmp = this[a]; this[a] = this[b]; this[b] = tmp }

    fun save() {
        val current = _state.value
        val nameError = Validators.validateRequired(current.name, "양식 이름을 적어 주세요. 예: 토스 백엔드 지원용")

        // 각 섹션 행 이름 필수 검증(인라인).
        val validatedRows = current.sections.map { row ->
            val rowError = Validators.validateRequired(row.name, "섹션 이름을 적어 주세요.")
            row.copy(nameError = rowError)
        }
        val hasSectionNameError = validatedRows.any { it.nameError != null }

        if (nameError != null || hasSectionNameError) {
            _state.update { it.copy(nameError = nameError, sections = validatedRows) }
            return
        }

        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val sectionRequests = current.sections.map {
                SectionRequest(name = it.name.trim(), character = it.character, required = it.required)
            }
            val result = if (current.isEditMode) {
                templateApi.update(
                    current.editingId!!,
                    UpdateTemplateRequest(name = current.name.trim(), sections = sectionRequests),
                )
            } else {
                templateApi.create(
                    CreateTemplateRequest(name = current.name.trim(), sections = sectionRequests),
                )
            }
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, saved = true) }
                is ApiResult.Failure -> {
                    // name 필드만 인라인으로 매핑하고, 그 외(필드 없음·sections·중첩 섹션 필드)는
                    // 스낵바로 안내해 막다른 길을 만들지 않는다(UX 에러 가이드).
                    val mappedToName = result.field == "name"
                    _state.update {
                        it.copy(
                            submitting = false,
                            nameError = if (mappedToName) result.message else it.nameError,
                            snackbarMessage = if (mappedToName) null else result.message,
                        )
                    }
                }
            }
        }
    }
}
