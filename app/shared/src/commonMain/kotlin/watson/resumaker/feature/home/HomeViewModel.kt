package watson.resumaker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.TargetApi
import watson.resumaker.network.TemplateApi

data class HomeUiState(
    val loading: Boolean = true,
    val experiences: List<ExperienceResponse> = emptyList(),
    val targets: List<TargetResponse> = emptyList(),
    val templates: List<TemplateResponse> = emptyList(),
    val errorMessage: String? = null,
) {
    /** 각 섹션 미리보기는 최대 3개(§8.2). */
    val experiencePreview: List<ExperienceResponse> get() = experiences.take(PREVIEW_COUNT)
    val targetPreview: List<TargetResponse> get() = targets.take(PREVIEW_COUNT)
    val templatePreview: List<TemplateResponse> get() = templates.take(PREVIEW_COUNT)

    companion object {
        const val PREVIEW_COUNT = 3
    }
}

/**
 * 홈 대시보드 ViewModel: 경험·목표·양식을 병렬 로드해 미리보기를 구성한다.
 */
class HomeViewModel(
    private val experienceApi: ExperienceApi,
    private val targetApi: TargetApi,
    private val templateApi: TemplateApi,
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
}
