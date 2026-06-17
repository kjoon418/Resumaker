package watson.resumaker.feature.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ArtifactResponse
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.SectionKind
import watson.resumaker.model.type.SectionStatus
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ArtifactApi

/**
 * 산출물 열람 화면의 표시 항목(생성 응답·열람 응답 공통 모델).
 * 상태로 부분 실패를 구분하고(가짜 성공 금지), 출처(sourceExperienceIds)는 표시·신뢰용이다.
 */
data class ArtifactSectionUi(
    val id: String,
    val sectionKind: SectionKind,
    val definitionKey: String,
    val content: String,
    val status: SectionStatus,
    val sourceExperienceIds: List<String>,
) {
    val failed: Boolean get() = status == SectionStatus.GENERATION_FAILED || status == SectionStatus.VALIDATION_FAILED
}

data class ArtifactUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val artifactId: String? = null,
    val kind: ArtifactKind? = null,
    val sections: List<ArtifactSectionUi> = emptyList(),
) {
    /** 부분 실패 항목이 하나라도 있는지(상단 고지·재시도 안내용). */
    val hasFailedSections: Boolean get() = sections.any { it.failed }

    /** 정상 생성된 항목만 모은 전체 복사 텍스트(실패 항목은 복사 대상에서 제외 — 가짜 성공 금지). */
    val fullCopyText: String
        get() = sections.filter { !it.failed }.joinToString("\n\n") { it.content }

    /** 복사 가능한 정상 항목이 하나라도 있는지(전체 복사 버튼 노출). */
    val hasCopyableContent: Boolean get() = sections.any { !it.failed && it.content.isNotBlank() }
}

/**
 * 산출물 열람 ViewModel. 활성 버전의 항목별 내용·상태·출처를 표시한다(수용 기준 12).
 *
 * 생성 직후에는 [initial] 생성 응답을 그대로 표시해 불필요한 재조회를 피하고, 그 외(딥링크·새로고침)에는
 * [artifactId]로 GET /artifacts/{id}를 호출한다. 복사는 클라이언트 책임이다(도메인 §6).
 */
class ArtifactViewModel(
    private val artifactApi: ArtifactApi,
    private val artifactId: String,
    initial: GenerationResponse? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtifactUiState(artifactId = artifactId))
    val state: StateFlow<ArtifactUiState> = _state.asStateFlow()

    /** 진행 중인 열람 로드 Job. 연타 시 중복 로드 경합을 막는다. */
    private var loadJob: Job? = null

    init {
        if (initial != null) {
            _state.value = initial.toUiState()
        } else {
            load()
        }
    }

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            when (val result = artifactApi.getArtifact(artifactId)) {
                is ApiResult.Success -> _state.value = result.value.toUiState()
                is ApiResult.Failure -> _state.update { it.copy(loading = false, errorMessage = result.message) }
            }
        }
    }

    private fun GenerationResponse.toUiState() = ArtifactUiState(
        loading = false,
        artifactId = artifactId,
        kind = kind,
        sections = sections.map {
            ArtifactSectionUi(
                id = it.sectionId,
                sectionKind = it.sectionKind,
                definitionKey = it.definitionKey,
                content = it.content,
                status = it.status,
                sourceExperienceIds = it.sourceExperienceIds,
            )
        },
    )

    private fun ArtifactResponse.toUiState() = ArtifactUiState(
        loading = false,
        artifactId = id,
        kind = kind,
        sections = activeVersion.sections.map {
            ArtifactSectionUi(
                id = it.id,
                sectionKind = it.sectionKind,
                definitionKey = it.definitionKey,
                content = it.content,
                status = it.status,
                sourceExperienceIds = it.sourceExperienceIds,
            )
        },
    )
}
