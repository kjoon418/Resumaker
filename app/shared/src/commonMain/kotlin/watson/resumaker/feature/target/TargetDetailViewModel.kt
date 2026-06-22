package watson.resumaker.feature.target

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.type.StrategyStatus
import watson.resumaker.network.ApiResult
import watson.resumaker.network.TargetApi

/**
 * 목표 상세 화면 상태. 회사·직무·채용 방향 원문과 AI 작성 전략(상태별 분기)을 보여준다.
 */
data class TargetDetailUiState(
    val loading: Boolean = true,
    val target: TargetResponse? = null,
    val errorMessage: String? = null,
    /** 채용 방향 원문 "전체 보기"/"접기" 토글 상태(기본 접힘). */
    val directionExpanded: Boolean = false,
    /** 재추출 요청 진행 중(버튼 중복 호출 방지). */
    val retrying: Boolean = false,
    /** 1회성 안내(스낵바). 화면이 소비 후 [consumeSnackbar]로 클리어. */
    val snackbarMessage: String? = null,
) {
    /** 현재 전략 상태(타깃 없으면 PENDING). */
    val strategyStatus: StrategyStatus get() = target?.strategyStatus ?: StrategyStatus.PENDING
}

/**
 * 목표 상세 ViewModel: GET /targets/{id} 로드, 전략 상태가 활성(PENDING/EXTRACTING)이면 폴링해 전환을 반영하고,
 * FAILED 상태면 retry(POST /targets/{id}/strategy/retry, 202)로 재추출을 큐잉한 뒤 폴링을 재개한다.
 *
 * 폴링은 [ArtifactListViewModel]과 동일한 패턴(viewModelScope·delay 루프)을 따른다 — 활성 상태가 아니면 멈춘다.
 * viewModelScope에 묶여 화면 이탈 시 자동 취소된다.
 */
class TargetDetailViewModel(
    private val targetApi: TargetApi,
    private val targetId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TargetDetailUiState())
    val state: StateFlow<TargetDetailUiState> = _state.asStateFlow()

    /** 진행 중인 폴링 루프. 중복 가동을 막기 위해 보관한다. */
    private var pollJob: Job? = null

    init {
        load()
    }

    /** 진입/재진입 시 즉시 1회 조회하고, 전략이 활성 상태면 폴링을 (재)가동한다. */
    fun load() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            refresh(setLoadingFalse = true)
            ensurePolling()
        }
    }

    /** GET /targets/{id}로 상태를 갱신한다. 실패는 errorMessage로 표면화하되 기존 값은 보존한다. */
    private suspend fun refresh(setLoadingFalse: Boolean) {
        when (val result = targetApi.getOne(targetId)) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    loading = if (setLoadingFalse) false else it.loading,
                    target = result.value,
                    errorMessage = null,
                )
            }
            is ApiResult.Failure -> _state.update {
                it.copy(
                    loading = if (setLoadingFalse) false else it.loading,
                    errorMessage = if (it.target == null) result.message else it.errorMessage,
                )
            }
        }
    }

    /** 전략이 활성(분석 중)이면 폴링 루프를 가동한다(이미 돌고 있으면 무시). 타깃 미로드나 비활성 상태면 가동하지 않는다. */
    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        if (_state.value.target == null) return
        if (!_state.value.strategyStatus.isActive) return
        pollJob = viewModelScope.launch {
            while (_state.value.strategyStatus.isActive) {
                delay(POLL_INTERVAL_MS)
                refresh(setLoadingFalse = false)
            }
        }
    }

    fun toggleDirectionExpanded() = _state.update { it.copy(directionExpanded = !it.directionExpanded) }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    /**
     * 전략 재추출(FAILED → 다시 분석하기). 202를 받으면 낙관적으로 상태를 PENDING으로 돌리고 폴링을 재개한다.
     * 진행 중이면 중복 호출하지 않는다.
     */
    fun retryStrategy() {
        if (_state.value.retrying) return
        _state.update { it.copy(retrying = true) }
        viewModelScope.launch {
            when (val result = targetApi.retryStrategy(targetId)) {
                is ApiResult.Success -> {
                    // 낙관적 전환: 재추출이 큐잉됐으므로 분석 중(PENDING)으로 표시하고 폴링을 재개한다.
                    _state.update {
                        it.copy(
                            retrying = false,
                            target = it.target?.copy(strategyStatus = StrategyStatus.PENDING, writingStrategy = null),
                        )
                    }
                    ensurePolling()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(retrying = false, snackbarMessage = result.message)
                }
            }
        }
    }

    companion object {
        /** 폴링 간격(3초). 푸시가 없어 전략 추출 완료를 주기적으로 확인한다([ArtifactListViewModel]과 동일). */
        const val POLL_INTERVAL_MS = 3_000L
    }
}
