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
    /**
     * 재생성/편집이 진행 중인 항목 id 집합(in-flight 가드). 같은 항목에 대한 중복 액션을 막고, 진행 중 항목에
     * 로딩 표시를 띄운다. 재생성은 장시간(LLM)이므로 사용자가 진행 상황을 인지해야 한다(UX 핵심 가이드).
     */
    val inFlightSectionIds: Set<String> = emptySet(),
    /**
     * 항목 액션(재생성/편집) 결과를 알리는 일회성 안내 메시지(스낵바). 동시 재생성 409·정리 고지·실패 안내 등에
     * 쓰인다. 화면이 표시 후 [consumeActionMessage]로 비운다(중복 표시 방지).
     */
    val actionMessage: String? = null,
) {
    /** 부분 실패 항목이 하나라도 있는지(상단 고지·재시도 안내용). */
    val hasFailedSections: Boolean get() = sections.any { it.failed }

    /** 정상 생성된 항목만 모은 전체 복사 텍스트(실패 항목은 복사 대상에서 제외 — 가짜 성공 금지). */
    val fullCopyText: String
        get() = sections.filter { !it.failed }.joinToString("\n\n") { it.content }

    /** 복사 가능한 정상 항목이 하나라도 있는지(전체 복사 버튼 노출). */
    val hasCopyableContent: Boolean get() = sections.any { !it.failed && it.content.isNotBlank() }

    /** 해당 항목이 재생성/편집 진행 중인지(중복 액션 차단·로딩 표시). */
    fun isSectionInFlight(sectionId: String): Boolean = sectionId in inFlightSectionIds
}

/**
 * 산출물 열람 ViewModel. 활성 버전의 항목별 내용·상태·출처를 표시한다(수용 기준 12).
 *
 * [initial]이 있으면 즉시 시드해 첫 프레임부터 생성 결과를 보여주고(깜박임 없는 즉시 표시), 이어서 항상
 * `load()`로 서버 권위 상태를 가져온다(load-after-initial). 이 방식은 버전 화면 push/pop 시 VM이 재생성돼도
 * 복원·재생성·편집 결과가 항상 반영되는 정확성을 보장한다(§287 복원=활성 전환). `load()` 자체는 진행 중
 * 중복 호출 가드가 있어 연타 시 경합이 없다.
 *
 * 복사는 클라이언트 책임이다(도메인 §6).
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
        // initial이 있으면 즉시 시드해 첫 프레임부터 표시하고, 항상 load()로 서버 최신 상태를 덮어쓴다.
        // 이렇게 해야 버전 화면 복원 후 VM이 재생성돼도 갱신된 활성 버전이 반영된다(§287).
        if (initial != null) {
            _state.value = initial.toUiState()
        }
        load()
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

    /**
     * 항목 단위 재생성(POST .../regenerate). 같은 항목이 이미 진행 중이면 무시한다(in-flight 가드 — 중복 액션 차단).
     * 성공 시 응답(활성 버전)으로 항목·버전을 교체해 갱신하고, 재생성 결과가 검증/생성 실패여도 상태배지로 정직하게
     * 고지한다(가짜 성공 금지). 동시 재생성 409·미존재 404·기타 실패는 일회성 안내로 알린다(막다른 길 금지).
     *
     * @param directive 선택적 개선 지시(빈 값 허용 — 공백만이면 지시 없음으로 보내 본문 null).
     */
    fun regenerateSection(sectionId: String, directive: String?) {
        if (_state.value.isSectionInFlight(sectionId)) return
        markInFlight(sectionId)
        viewModelScope.launch {
            val result = artifactApi.regenerateSection(
                artifactId = artifactId,
                sectionId = sectionId,
                directive = directive?.trim()?.takeIf { it.isNotEmpty() },
            )
            when (result) {
                is ApiResult.Success -> applyUpdated(
                    response = result.value,
                    clearedSectionId = sectionId,
                    message = prunedNotice(result.value.prunedVersionCount),
                )
                is ApiResult.Failure -> finishWithFailure(sectionId, result.message)
            }
        }
    }

    /**
     * 항목 직접 편집(PUT .../content). 같은 항목이 진행 중이면 무시한다(in-flight 가드). 빈 내용은 서버가 400으로
     * 거절하므로 클라이언트에서도 미리 막아 호출하지 않는다(인라인 검증 — 불필요한 왕복·막다른 길 금지). 직접 편집은
     * 자동 검증 미적용(§428)이라 응답은 항상 사용자 내용을 반영한다. 성공 시 응답으로 항목·버전을 교체해 갱신한다.
     */
    fun editSection(sectionId: String, content: String) {
        if (content.isBlank()) return
        if (_state.value.isSectionInFlight(sectionId)) return
        markInFlight(sectionId)
        viewModelScope.launch {
            when (val result = artifactApi.editSectionContent(artifactId, sectionId, content)) {
                is ApiResult.Success -> applyUpdated(
                    response = result.value,
                    clearedSectionId = sectionId,
                    message = prunedNotice(result.value.prunedVersionCount),
                )
                is ApiResult.Failure -> finishWithFailure(sectionId, result.message)
            }
        }
    }

    /** 스낵바에 표시한 일회성 안내를 비운다(중복 표시 방지). */
    fun consumeActionMessage() {
        _state.update { if (it.actionMessage == null) it else it.copy(actionMessage = null) }
    }

    private fun markInFlight(sectionId: String) {
        _state.update { it.copy(inFlightSectionIds = it.inFlightSectionIds + sectionId) }
    }

    /** 성공 응답으로 항목·버전을 교체하고 in-flight 해제, 정리 고지 등을 일회성 메시지로 싣는다. */
    private fun applyUpdated(response: ArtifactResponse, clearedSectionId: String, message: String?) {
        val updated = response.toUiState()
        _state.update {
            it.copy(
                loading = false,
                kind = updated.kind,
                sections = updated.sections,
                inFlightSectionIds = it.inFlightSectionIds - clearedSectionId,
                actionMessage = message ?: it.actionMessage,
            )
        }
    }

    private fun finishWithFailure(sectionId: String, message: String) {
        _state.update {
            it.copy(
                inFlightSectionIds = it.inFlightSectionIds - sectionId,
                actionMessage = message,
            )
        }
    }

    /** 보관 상한 정리가 있었으면(>0) 비차단 사후 고지 문구를, 없으면 null(표시 안 함)을 만든다(§398·§273). */
    private fun prunedNotice(prunedVersionCount: Int): String? =
        if (prunedVersionCount > 0) "오래된 버전 ${prunedVersionCount}개가 정리됐어요." else null

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
