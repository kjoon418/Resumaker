package watson.resumaker.feature.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.GenerationResponse
import watson.resumaker.model.dto.PortfolioGenerationRequest
import watson.resumaker.model.dto.ResumeGenerationRequest
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ArtifactApi
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.TargetApi
import watson.resumaker.network.TemplateApi

/**
 * 산출물 생성 진입 화면 상태.
 *
 * 흐름: 종류 선택(이력서/포트폴리오) → 경험 다중 선택(필수 1+) → 목표 선택(필수) → (이력서면) 양식 선택(필수) → 생성.
 * 재료(경험·목표·양식)는 기존 목록 API를 재사용해 병렬 로드한다.
 *
 * 경험이 하나도 없으면 [hasNoExperiences]가 true가 되어 화면이 예방형 ComingSoon 분기를 보여준다(도메인 §110·§410).
 * 서버 409(code=EMPTY_EXPERIENCE_SELECTION, action=ADD_EXPERIENCE)는 생성 단계 오류로 [generationError]/[generationErrorCode]
 * /[generationAction]으로 표면화한다. [generationAction]이 "ADD_EXPERIENCE"이면 경험 추가 유도 CTA를 쓸 수 있다.
 */
data class ArtifactCreateUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val experiences: List<ExperienceResponse> = emptyList(),
    val targets: List<TargetResponse> = emptyList(),
    val templates: List<TemplateResponse> = emptyList(),
    val kind: ArtifactKind = ArtifactKind.RESUME,
    val selectedExperienceIds: Set<String> = emptySet(),
    val selectedTargetId: String? = null,
    val selectedTemplateId: String? = null,
    val generating: Boolean = false,
    /** 생성 성공 시 채워지며, 화면이 열람 화면으로 이동하는 신호로 1회 소비한다(재조회 없이 그대로 표시). */
    val generated: GenerationResponse? = null,
    /** 생성 실패(부분 성공은 실패가 아님) 안내. [generationErrorCode]로 오류 종류(EMPTY_EXPERIENCE_SELECTION 등) 분기. */
    val generationError: String? = null,
    val generationErrorCode: String? = null,
    /** 서버가 내려주는 "사용자가 해야 할 일" 힌트(ADD_EXPERIENCE 등). 화면이 CTA 분기에 사용. */
    val generationAction: String? = null,
) {
    /** 재료가 비어 산출물을 만들 수 없는지(빈 경험묶음 예방형 분기 — 수용 기준 8). */
    val hasNoExperiences: Boolean get() = experiences.isEmpty()

    /** 양식 필수 여부(이력서만). */
    val templateRequired: Boolean get() = kind == ArtifactKind.RESUME

    /** 필수값이 모두 채워져 생성 가능한지(필수 미선택이면 생성 버튼 비활성). */
    val canSubmit: Boolean
        get() = !generating &&
            selectedExperienceIds.isNotEmpty() &&
            selectedTargetId != null &&
            (!templateRequired || selectedTemplateId != null)
}

/**
 * 산출물 생성 진입 ViewModel. 재료 로드·선택 상태·폼 검증·생성 호출을 담당한다(단방향).
 * 부분 성공(200, 일부 *_FAILED)도 성공으로 받아 열람 화면이 항목 상태를 고지한다(가짜 성공 금지).
 */
class ArtifactCreateViewModel(
    private val artifactApi: ArtifactApi,
    private val experienceApi: ExperienceApi,
    private val targetApi: TargetApi,
    private val templateApi: TemplateApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtifactCreateUiState())
    val state: StateFlow<ArtifactCreateUiState> = _state.asStateFlow()

    /** 진행 중인 재료 로드 Job. 연타 시 중복 로드 경합을 막는다(generate()와 동일 패턴). */
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            val experiencesDeferred = async { experienceApi.getAll() }
            val targetsDeferred = async { targetApi.getAll() }
            val templatesDeferred = async { templateApi.getAll() }
            val experiencesResult = experiencesDeferred.await()
            val targetsResult = targetsDeferred.await()
            val templatesResult = templatesDeferred.await()

            val errorMessage = (experiencesResult as? ApiResult.Failure)?.message
                ?: (targetsResult as? ApiResult.Failure)?.message
                ?: (templatesResult as? ApiResult.Failure)?.message

            _state.update {
                it.copy(
                    loading = false,
                    experiences = (experiencesResult as? ApiResult.Success)?.value ?: it.experiences,
                    targets = (targetsResult as? ApiResult.Success)?.value ?: it.targets,
                    templates = (templatesResult as? ApiResult.Success)?.value ?: it.templates,
                    errorMessage = errorMessage,
                )
            }
        }
    }

    fun selectKind(kind: ArtifactKind) = _state.update {
        // 포트폴리오로 바꾸면 양식 선택은 의미가 없어 비운다(양식 없음).
        it.copy(kind = kind, selectedTemplateId = if (kind == ArtifactKind.RESUME) it.selectedTemplateId else null)
    }

    fun toggleExperience(id: String) = _state.update {
        val next = if (id in it.selectedExperienceIds) it.selectedExperienceIds - id else it.selectedExperienceIds + id
        it.copy(selectedExperienceIds = next)
    }

    fun selectTarget(id: String) = _state.update { it.copy(selectedTargetId = id) }

    fun selectTemplate(id: String) = _state.update { it.copy(selectedTemplateId = id) }

    fun consumeGenerated() = _state.update { it.copy(generated = null) }

    fun dismissGenerationError() = _state.update { it.copy(generationError = null, generationErrorCode = null, generationAction = null) }

    /** 생성 요청. 필수 미선택이면 호출하지 않는다(버튼이 이미 비활성). */
    fun generate() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(generating = true, generationError = null, generationErrorCode = null) }
        viewModelScope.launch {
            val result = when (current.kind) {
                ArtifactKind.RESUME -> artifactApi.generateResume(
                    ResumeGenerationRequest(
                        experienceIds = current.selectedExperienceIds.toList(),
                        targetId = current.selectedTargetId!!,
                        templateId = current.selectedTemplateId!!,
                    ),
                )
                ArtifactKind.PORTFOLIO -> artifactApi.generatePortfolio(
                    PortfolioGenerationRequest(
                        experienceIds = current.selectedExperienceIds.toList(),
                        targetId = current.selectedTargetId!!,
                    ),
                )
            }
            applyGenerationResult(result)
        }
    }

    private fun applyGenerationResult(result: ApiResult<GenerationResponse>) = _state.update {
        when (result) {
            // 부분 성공 포함: 성공으로 받아 열람 화면으로 이동(항목 상태는 열람 화면이 고지).
            is ApiResult.Success -> it.copy(generating = false, generated = result.value)
            is ApiResult.Failure -> it.copy(
                generating = false,
                generationError = result.message,
                generationErrorCode = result.code,
                generationAction = result.action,
            )
        }
    }
}
