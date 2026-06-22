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
import watson.resumaker.model.dto.GenerationJobResponse
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
    /**
     * "양식 자동(AI에 맡기기)" 선택 여부(서버 §178·§446). true면 양식을 지정하지 않고 생성해 AI가 경험·목표로
     * 섹션 구조를 정한다(templateId=null 전송). 구체 양식을 고르면 false로 돌아간다.
     */
    val useAiTemplate: Boolean = false,
    val generating: Boolean = false,
    /**
     * 제출(202) 성공 신호. true면 화면이 1회 소비해 산출물 목록(Screen.ArtifactList)으로 이동한다.
     * 비동기 전환 후 생성은 즉시 산출물을 주지 않으므로, 완료가 아니라 "제출됨"을 신호한다.
     */
    val submitted: Boolean = false,
    /** 생성 실패(부분 성공은 실패가 아님) 안내. [generationErrorCode]로 오류 종류(EMPTY_EXPERIENCE_SELECTION 등) 분기. */
    val generationError: String? = null,
    val generationErrorCode: String? = null,
    /** 서버가 내려주는 "사용자가 해야 할 일" 힌트(ADD_EXPERIENCE 등). 화면이 CTA 분기에 사용. */
    val generationAction: String? = null,
) {
    /** 재료가 비어 산출물을 만들 수 없는지(빈 경험묶음 예방형 분기 — 수용 기준 8). */
    val hasNoExperiences: Boolean get() = experiences.isEmpty()

    /**
     * 1차 생성 일일 한도 초과(429, 비용 가드레일 §396)인지. 배너 톤을 "실패"가 아니라 "오늘은 더 못 만듦"으로
     * 분기한다. 1차 생성은 아직 산출물이 없어 직접 편집 대안이 없으므로, 안내는 "내일 다시"(서버 메시지)에 맡긴다.
     */
    val isGenerationQuotaExceeded: Boolean get() = generationErrorCode == ArtifactCreateViewModel.GENERATION_QUOTA_EXCEEDED

    /**
     * 양식 선택 단계 노출 여부(이력서만). 양식 자체는 더 이상 필수가 아니다 — "양식 자동"을 고르면 미지정으로
     * 생성된다(서버 §178). 포트폴리오는 양식이 없으므로 단계를 숨긴다.
     */
    val templateStepVisible: Boolean get() = kind == ArtifactKind.RESUME

    /** 양식 선택이 유효한지(이력서면 구체 양식 선택 또는 "양식 자동" 중 하나). 포트폴리오는 항상 충족. */
    val templateChoiceSatisfied: Boolean
        get() = kind != ArtifactKind.RESUME || useAiTemplate || selectedTemplateId != null

    /** 필수값이 모두 채워져 생성 가능한지(필수 미선택이면 생성 버튼 비활성). */
    val canSubmit: Boolean
        get() = !generating &&
            selectedExperienceIds.isNotEmpty() &&
            selectedTargetId != null &&
            templateChoiceSatisfied

    /**
     * 선택된 목표가 있고 그 전략이 아직 READY가 아닌지. true면 "공고 원문 기반으로 만들어 드린다"는 안내 caption을
     * 노출한다(전략 없어도 생성은 항상 가능 — 막다른 길 금지). 미선택이면 false.
     */
    val selectedTargetStrategyNotReady: Boolean
        get() {
            val target = targets.firstOrNull { it.id == selectedTargetId } ?: return false
            return target.strategyStatus != watson.resumaker.model.type.StrategyStatus.READY
        }
}

/**
 * 산출물 생성 진입 ViewModel. 재료 로드·선택 상태·폼 검증·생성 제출을 담당한다(단방향).
 * 비동기 전환 후 제출(202)은 산출물을 즉시 주지 않으므로, 성공 시 [ArtifactCreateUiState.submitted]를 세워
 * 화면이 산출물 목록으로 이동하게 한다(완료 확인은 목록의 폴링이 담당).
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
        // 포트폴리오로 바꾸면 양식 선택은 의미가 없어 비운다(양식 없음, 자동 선택도 해제).
        if (kind == ArtifactKind.RESUME) {
            it.copy(kind = kind)
        } else {
            it.copy(kind = kind, selectedTemplateId = null, useAiTemplate = false)
        }
    }

    fun toggleExperience(id: String) = _state.update {
        val next = if (id in it.selectedExperienceIds) it.selectedExperienceIds - id else it.selectedExperienceIds + id
        it.copy(selectedExperienceIds = next)
    }

    fun selectTarget(id: String) = _state.update { it.copy(selectedTargetId = id) }

    /** 구체 양식 선택. "양식 자동"은 해제된다(둘은 배타적). */
    fun selectTemplate(id: String) = _state.update { it.copy(selectedTemplateId = id, useAiTemplate = false) }

    /** "양식 자동(AI에 맡기기)" 선택. 구체 양식 선택은 해제된다(둘은 배타적). */
    fun selectAiTemplate() = _state.update { it.copy(useAiTemplate = true, selectedTemplateId = null) }

    fun consumeSubmitted() = _state.update { it.copy(submitted = false) }

    fun dismissGenerationError() = _state.update { it.copy(generationError = null, generationErrorCode = null, generationAction = null) }

    /**
     * 생성 실패 후 "다시 시도" — 직전 실패한 생성을 동일 선택 그대로 재요청한다(#4).
     * 오류를 닫기만 하는 dismissGenerationError 와 달리 실제 API 재호출이 이뤄진다.
     * 선택값이 이미 유효하므로 generate() 진입 조건(canSubmit)을 그대로 재사용한다.
     */
    fun retryGenerate() {
        dismissGenerationError()
        generate()
    }

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
                        // "양식 자동"이면 templateId를 생략해 AI 생성 양식 경로로 진입한다(서버 §178).
                        templateId = if (current.useAiTemplate) null else current.selectedTemplateId,
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

    private fun applyGenerationResult(result: ApiResult<GenerationJobResponse>) = _state.update {
        when (result) {
            // 제출 성공(202): 작업이 만들어졌으므로 산출물 목록으로 이동해 진행 상황을 보게 한다(완료는 목록이 폴링).
            is ApiResult.Success -> it.copy(generating = false, submitted = true)
            is ApiResult.Failure -> it.copy(
                generating = false,
                generationError = result.message,
                generationErrorCode = result.code,
                generationAction = result.action,
            )
        }
    }

    companion object {
        /** 1차 생성 일일 한도 초과 에러 코드(서버 `CountingGenerationQuotaGuard`와 1:1, 429). */
        const val GENERATION_QUOTA_EXCEEDED = "GENERATION_QUOTA_EXCEEDED"
    }
}
