package watson.resumaker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ArtifactSummaryResponse
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.GenerationJobResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ArtifactApi
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.TargetApi
import watson.resumaker.network.TemplateApi

data class HomeUiState(
    val loading: Boolean = true,
    val experiences: List<ExperienceResponse> = emptyList(),
    val targets: List<TargetResponse> = emptyList(),
    val templates: List<TemplateResponse> = emptyList(),
    /** 내 산출물 섹션용: 생성 작업·완성 산출물(홈은 진입 시 1회만 로드, 폴링은 목록 화면이 담당). */
    val jobs: List<GenerationJobResponse> = emptyList(),
    val artifacts: List<ArtifactSummaryResponse> = emptyList(),
    val errorMessage: String? = null,
) {
    /** 각 섹션 미리보기는 최대 3개(§8.2). */
    val experiencePreview: List<ExperienceResponse> get() = experiences.take(PREVIEW_COUNT)
    val targetPreview: List<TargetResponse> get() = targets.take(PREVIEW_COUNT)
    val templatePreview: List<TemplateResponse> get() = templates.take(PREVIEW_COUNT)

    /** 진행 중(PENDING/RUNNING) 작업 수. 0이면 "N건 진행 중" 배지를 숨긴다. */
    val activeJobCount: Int get() = jobs.count { it.status.isActive }

    /** 완성 산출물 미리보기(최신순 1~2개). */
    val artifactPreview: List<ArtifactSummaryResponse> get() = artifacts.take(ARTIFACT_PREVIEW_COUNT)

    /** 내 산출물 섹션 노출 여부 — 진행 중 작업도, 완성 산출물도 없으면 숨긴다. */
    val showArtifactSection: Boolean get() = activeJobCount > 0 || artifacts.isNotEmpty()

    companion object {
        const val PREVIEW_COUNT = 3
        const val ARTIFACT_PREVIEW_COUNT = 2
    }
}

/**
 * 홈 대시보드 ViewModel: 경험·목표·양식과 산출물(작업·완성)을 병렬 로드해 미리보기를 구성한다.
 * 산출물 섹션은 진입 시 1회 로드면 충분하므로 폴링하지 않는다(폴링은 산출물 목록 화면이 담당).
 */
class HomeViewModel(
    private val experienceApi: ExperienceApi,
    private val targetApi: TargetApi,
    private val templateApi: TemplateApi,
    private val artifactApi: ArtifactApi,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val experiencesDeferred = async { experienceApi.getAll() }
            val targetsDeferred = async { targetApi.getAll() }
            val templatesDeferred = async { templateApi.getAll() }
            val jobsDeferred = async { artifactApi.listJobs() }
            val artifactsDeferred = async { artifactApi.listArtifacts() }
            val experiencesResult = experiencesDeferred.await()
            val targetsResult = targetsDeferred.await()
            val templatesResult = templatesDeferred.await()
            val jobsResult = jobsDeferred.await()
            val artifactsResult = artifactsDeferred.await()

            // 산출물 섹션 로드 실패는 보조 정보라 전체 에러로 올리지 않는다(핵심 목록 실패만 errorMessage로).
            val errorMessage = (experiencesResult as? ApiResult.Failure)?.message
                ?: (targetsResult as? ApiResult.Failure)?.message
                ?: (templatesResult as? ApiResult.Failure)?.message

            _state.update {
                it.copy(
                    loading = false,
                    experiences = (experiencesResult as? ApiResult.Success)?.value ?: it.experiences,
                    targets = (targetsResult as? ApiResult.Success)?.value ?: it.targets,
                    templates = (templatesResult as? ApiResult.Success)?.value ?: it.templates,
                    jobs = (jobsResult as? ApiResult.Success)?.value ?: it.jobs,
                    artifacts = (artifactsResult as? ApiResult.Success)?.value ?: it.artifacts,
                    errorMessage = errorMessage,
                )
            }
        }
    }
}
