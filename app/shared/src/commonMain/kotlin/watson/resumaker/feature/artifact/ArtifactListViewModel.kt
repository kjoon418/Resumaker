package watson.resumaker.feature.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ArtifactSummaryResponse
import watson.resumaker.model.dto.GenerationJobResponse
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ArtifactApi

/**
 * 내 산출물 목록 화면 상태.
 *
 * 표시 규칙(중복 방지): 생성 작업 중 SUCCEEDED는 렌더하지 않는다 — 그 산출물이 [artifacts]에 이미 있다.
 * 따라서 상단에는 활성(PENDING/RUNNING)·실패(FAILED) 작업([renderJobs])을, 하단에는 완성 산출물([artifacts])을
 * 각각 최신순으로 보여준다(서버가 최신순으로 내려준다).
 */
data class ArtifactListUiState(
    val loading: Boolean = true,
    val jobs: List<GenerationJobResponse> = emptyList(),
    val artifacts: List<ArtifactSummaryResponse> = emptyList(),
    val errorMessage: String? = null,
    /** 삭제 진행 중인 작업 id 집합(카드 액션 비활성·중복 호출 방지). */
    val deletingJobIds: Set<String> = emptySet(),
    /** '다시 만들기'(IN_PLACE) 진행 중인 작업 id 집합(버튼 비활성·중복 호출 방지). */
    val retryingJobIds: Set<String> = emptySet(),
    /** 완료/삭제 등 1회성 안내(스낵바). 화면이 소비 후 [consumeSnackbar]로 클리어. */
    val snackbarMessage: String? = null,
) {
    /** 상단에 렌더할 작업 = 활성(PENDING/RUNNING) + 실패(FAILED). SUCCEEDED는 산출물 목록에 있으므로 제외. */
    val renderJobs: List<GenerationJobResponse> get() = jobs.filterNot { it.status == watson.resumaker.model.type.GenerationJobStatus.SUCCEEDED }

    /** 활성 작업이 하나라도 있는지(폴링 유지 조건). */
    val hasActiveJobs: Boolean get() = jobs.any { it.status.isActive }

    /** 진행 중/실패 작업도, 완성 산출물도 없는 완전 빈 상태(EmptyState 분기). */
    val isEmpty: Boolean get() = renderJobs.isEmpty() && artifacts.isEmpty()
}

/**
 * 내 산출물 목록 ViewModel: 생성 작업(listJobs)과 완성 산출물(listArtifacts)을 병렬 로드해 합쳐 보여준다.
 *
 * 비동기 생성 전환 후, 제출된 작업은 PENDING/RUNNING으로 보이다가 완료되면 사라지고(완성 산출물로 전환) 산출물
 * 목록에 나타난다. 푸시가 없으므로 활성 작업이 있는 동안 [POLL_INTERVAL_MS] 간격으로 재조회해(폴링) 완료를
 * 확인한다. 활성 작업이 0이면 폴링을 멈춘다(불필요한 호출 방지).
 */
class ArtifactListViewModel(
    private val artifactApi: ArtifactApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtifactListUiState())
    val state: StateFlow<ArtifactListUiState> = _state.asStateFlow()

    /** 진행 중인 폴링 루프. 중복 가동을 막기 위해 보관한다. */
    private var pollJob: Job? = null

    init {
        load()
    }

    /** 화면 진입/재진입 시 즉시 1회 갱신하고, 활성 작업이 있으면 폴링을 (재)가동한다. */
    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            refresh(setLoadingFalse = true)
            ensurePolling()
        }
    }

    /** jobs + artifacts를 병렬 조회해 상태를 갱신한다. 실패는 errorMessage로 표면화하되 기존 값은 보존한다. */
    private suspend fun refresh(setLoadingFalse: Boolean) = coroutineScope {
        val jobsDeferred = async { artifactApi.listJobs() }
        val artifactsDeferred = async { artifactApi.listArtifacts() }
        val jobsResult = jobsDeferred.await()
        val artifactsResult = artifactsDeferred.await()

        val errorMessage = (jobsResult as? ApiResult.Failure)?.message
            ?: (artifactsResult as? ApiResult.Failure)?.message

        _state.update {
            it.copy(
                loading = if (setLoadingFalse) false else it.loading,
                jobs = (jobsResult as? ApiResult.Success)?.value ?: it.jobs,
                artifacts = (artifactsResult as? ApiResult.Success)?.value ?: it.artifacts,
                errorMessage = errorMessage,
            )
        }
    }

    /** 활성 작업이 있으면 폴링 루프를 가동한다(이미 돌고 있으면 무시). 활성 0이면 가동하지 않는다. */
    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        if (!_state.value.hasActiveJobs) return
        pollJob = viewModelScope.launch {
            while (_state.value.hasActiveJobs) {
                delay(POLL_INTERVAL_MS)
                refresh(setLoadingFalse = false)
            }
        }
    }

    /** 생성 실패 작업 "기록 삭제"(DELETE /generation-jobs/{id}). 성공 시 목록에서 제거한다. */
    fun deleteJob(job: GenerationJobResponse) {
        if (job.jobId in _state.value.deletingJobIds) return
        _state.update { it.copy(deletingJobIds = it.deletingJobIds + job.jobId) }
        viewModelScope.launch {
            when (val result = artifactApi.deleteJob(job.jobId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        jobs = it.jobs.filterNot { j -> j.jobId == job.jobId },
                        deletingJobIds = it.deletingJobIds - job.jobId,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        deletingJobIds = it.deletingJobIds - job.jobId,
                        snackbarMessage = result.message,
                    )
                }
            }
        }
    }

    /**
     * 일시적 실패 작업 '다시 만들기'(IN_PLACE, POST /generation-jobs/{id}/retry). 서버가 저장된 입력으로 새
     * PENDING 작업을 만들고 실패 작업을 삭제하므로, 성공 시 목록을 다시 불러오면 실패 카드가 사라지고 새 진행
     * 카드가 그 자리에 나타난다(폴링 재가동). 실패(409·429 등)는 스낵바로 안내하고 실패 카드는 그대로 둔다.
     */
    fun retryJob(job: GenerationJobResponse) {
        if (job.jobId in _state.value.retryingJobIds) return
        _state.update { it.copy(retryingJobIds = it.retryingJobIds + job.jobId) }
        viewModelScope.launch {
            when (val result = artifactApi.retryJob(job.jobId)) {
                is ApiResult.Success -> {
                    // 저장된 입력으로 재요청됨 → 목록 갱신(실패 카드 제거·새 진행 카드 등장)하고 폴링을 (재)가동한다.
                    refresh(setLoadingFalse = false)
                    _state.update { it.copy(retryingJobIds = it.retryingJobIds - job.jobId) }
                    ensurePolling()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        retryingJobIds = it.retryingJobIds - job.jobId,
                        snackbarMessage = result.message,
                    )
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    companion object {
        /** 폴링 간격(3초). 푸시가 없어 완료를 주기적으로 확인한다. */
        const val POLL_INTERVAL_MS = 3_000L
    }
}
