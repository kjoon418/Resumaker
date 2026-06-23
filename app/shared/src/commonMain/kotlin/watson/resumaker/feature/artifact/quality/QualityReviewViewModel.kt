package watson.resumaker.feature.artifact.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.CandidateDto
import watson.resumaker.model.dto.FindingDto
import watson.resumaker.model.dto.QualityJobStatus
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.TreatmentKind
import watson.resumaker.network.ApiResult
import watson.resumaker.network.QualityApi

// ── UI 모델 ──────────────────────────────────────────────────────────────────

/**
 * 소견 하나의 UI 표현. [treatmentKind]로 두 처치 분기를 결정한다.
 *  - AUTO_REWRITE : "이대로 다듬기" 선택 가능(체크박스).
 *  - SUGGESTION   : "경험 보강하러 가기" 버튼(텍스트 변경 없음).
 *  - OUT_OF_SCOPE : 화면에 노출하지 않는다.
 */
data class FindingUi(
    val findingId: String,
    val criterionLabel: String,
    val treatmentKind: TreatmentKind,
    val evidenceText: String?,
    val suggestionMessage: String?,
    /** SUGGESTION이고 특정 경험으로 이동할 때 non-null. null이면 경험 목록으로. */
    val targetExperienceId: String?,
)

/**
 * 채택 대기 중인 후보 한 건의 UI 표현.
 * 원본↔개선 비교 뷰에 사용한다.
 */
data class CandidateUi(
    val candidateId: String,
    val sectionId: String,
    val definitionKey: String,
    val originalContent: String,
    val candidateContent: String,
    /** 이 후보에 체크가 들어왔는지(채택 선택). 기본 true(전부 채택 권장). */
    val selected: Boolean = true,
)

/**
 * 품질 점검·개선 화면 단계.
 *
 * IDLE → REVIEWING(점검 중) → FINDINGS(소견 표시) → IMPROVING(잡 폴링) → CANDIDATES(비교·채택) → ADOPTED(채택 완료)
 * 어느 단계에서든 에러가 발생하면 [errorMessage]가 세팅된다.
 */
enum class QualityStep {
    /** 초기(진입 직전). */
    IDLE,
    /** 품질 점검 API 호출 중(스켈레톤 로딩). */
    REVIEWING,
    /** 소견 목록 표시(소견 0건 포함). */
    FINDINGS,
    /** 품질 개선 작업 접수 후 폴링 중(스켈레톤 로딩). */
    IMPROVING,
    /** SUCCEEDED: 후보 비교·채택 화면. */
    CANDIDATES,
    /** 채택 완료 — 화면이 산출물 열람으로 복귀한다. */
    ADOPTED,
}

/**
 * 품질 점검·개선 화면의 전체 UI 상태.
 *
 * [step]이 화면 단계를 결정한다. 각 단계별로 필요한 필드만 non-null이 된다.
 */
data class QualityReviewUiState(
    val step: QualityStep = QualityStep.IDLE,
    val errorMessage: String? = null,
    /** 소견 목록(FINDINGS 단계). OUT_OF_SCOPE는 제외하고 표시한다. */
    val findings: List<FindingUi> = emptyList(),
    /** 사용자가 선택한 AUTO_REWRITE 소견 id 집합(FINDINGS 단계). */
    val selectedFindingIds: Set<String> = emptySet(),
    /** IMPROVING 단계의 현재 작업 id(폴링 키). */
    val improvingJobId: String? = null,
    /** CANDIDATES 단계의 후보 목록. */
    val candidates: List<CandidateUi> = emptyList(),
    /** 채택 진행 중(adopt API 호출). */
    val adopting: Boolean = false,
    /** 검증 실패로 제외된 후보 수(CANDIDATES 단계에서 고지). 0이면 고지 안 함. */
    val excludedCandidateCount: Int = 0,
    /** 일회성 스낵바 안내(한도 초과·에러 등). 화면이 [consumeSnackbar]로 소비. */
    val snackbarMessage: String? = null,
) {
    /** AUTO_REWRITE 소견 중 사용자가 하나라도 선택했는지(처치 접수 버튼 활성 조건). */
    val canSubmitImprovement: Boolean
        get() = selectedFindingIds.isNotEmpty()

    /** 소견이 0건인 빈 상태(긍정 빈 상태 고지). */
    val hasNoFindings: Boolean
        get() = step == QualityStep.FINDINGS && findings.isEmpty()

    /** AUTO_REWRITE 소견 목록. */
    val autoRewriteFindings: List<FindingUi>
        get() = findings.filter { it.treatmentKind == TreatmentKind.AUTO_REWRITE }

    /** SUGGESTION 소견 목록. */
    val suggestionFindings: List<FindingUi>
        get() = findings.filter { it.treatmentKind == TreatmentKind.SUGGESTION }

    /** 선택된 후보(CANDIDATES 단계). */
    val selectedCandidates: List<CandidateUi>
        get() = candidates.filter { it.selected }
}

/**
 * 품질 점검·개선 ViewModel.
 *
 * 진단(reviewQuality) → 소견 표시 → 처치 접수(submitImprovement) → 폴링(getImprovementJob) →
 * 후보 비교·채택(adoptCandidates) 흐름을 단방향 상태로 관리한다.
 *
 * 폴링 패턴은 기존 [watson.resumaker.feature.artifact.ArtifactListViewModel]의 생성 작업 폴링과 동형이다.
 */
class QualityReviewViewModel(
    private val qualityApi: QualityApi,
    private val artifactId: String,
    private val artifactKind: ArtifactKind,
) : ViewModel() {

    private val _state = MutableStateFlow(QualityReviewUiState())
    val state: StateFlow<QualityReviewUiState> = _state.asStateFlow()

    /** 진행 중인 점검 Job. 연타 방지. */
    private var reviewJob: Job? = null

    /** 진행 중인 폴링 Job. */
    private var pollJob: Job? = null

    // ── 진단 ──────────────────────────────────────────────────────────────────

    /**
     * 품질 점검 시작. 포트폴리오에는 진입점 자체가 없으므로(QC10) 방어적으로 확인한다.
     * 이미 진행 중이면 무시한다.
     */
    fun startReview() {
        if (_state.value.step == QualityStep.REVIEWING) return
        if (reviewJob?.isActive == true) return
        _state.update { it.copy(step = QualityStep.REVIEWING, errorMessage = null) }
        reviewJob = viewModelScope.launch {
            when (val result = qualityApi.reviewQuality(artifactId)) {
                is ApiResult.Success -> {
                    val response = result.value
                    val findings = response.findings
                        .filter { it.treatmentKind != TreatmentKind.OUT_OF_SCOPE }
                        .map { it.toUi() }
                    // 모든 AUTO_REWRITE를 기본 선택으로 시드한다(사용자가 추가로 선택/해제 가능).
                    val autoIds = findings
                        .filter { it.treatmentKind == TreatmentKind.AUTO_REWRITE }
                        .map { it.findingId }
                        .toSet()
                    _state.update {
                        it.copy(
                            step = QualityStep.FINDINGS,
                            findings = findings,
                            selectedFindingIds = autoIds,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(step = QualityStep.IDLE, errorMessage = result.message)
                }
            }
        }
    }

    // ── 소견 선택 ─────────────────────────────────────────────────────────────

    /** AUTO_REWRITE 소견 체크 토글. */
    fun toggleFinding(findingId: String) {
        _state.update {
            val next = if (findingId in it.selectedFindingIds) {
                it.selectedFindingIds - findingId
            } else {
                it.selectedFindingIds + findingId
            }
            it.copy(selectedFindingIds = next)
        }
    }

    // ── 처치 접수 ─────────────────────────────────────────────────────────────

    /**
     * "이대로 다듬기" 클릭 — 선택된 AUTO_REWRITE 소견으로 개선 작업을 접수하고 폴링을 시작한다.
     * 선택이 없으면 무시한다([canSubmitImprovement]가 false인 경우).
     */
    fun submitImprovement() {
        val current = _state.value
        if (!current.canSubmitImprovement) return
        if (current.step == QualityStep.IMPROVING) return
        _state.update { it.copy(step = QualityStep.IMPROVING, errorMessage = null) }
        viewModelScope.launch {
            val findingIds = current.selectedFindingIds.toList()
            when (val result = qualityApi.submitImprovement(artifactId, findingIds)) {
                is ApiResult.Success -> {
                    val job = result.value
                    _state.update { it.copy(improvingJobId = job.jobId) }
                    startPolling(job.jobId)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        step = QualityStep.FINDINGS,
                        snackbarMessage = improvementErrorMessage(result.code, result.message),
                    )
                }
            }
        }
    }

    // ── 폴링 ──────────────────────────────────────────────────────────────────

    /** 개선 작업 폴링 루프. 기존 생성 잡 폴링([ArtifactListViewModel])과 동형. */
    private fun startPolling(jobId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                when (val result = qualityApi.getImprovementJob(artifactId, jobId)) {
                    is ApiResult.Success -> {
                        val job = result.value
                        when {
                            job.status == QualityJobStatus.SUCCEEDED -> {
                                val rawCandidates = job.candidates.orEmpty()
                                val candidates = rawCandidates.map { it.toUi() }
                                // 응답 candidates가 요청한 소견보다 적으면 검증 실패로 제외된 것.
                                val requested = _state.value.selectedFindingIds.size
                                val excluded = (requested - rawCandidates.size).coerceAtLeast(0)
                                _state.update {
                                    it.copy(
                                        step = QualityStep.CANDIDATES,
                                        candidates = candidates,
                                        excludedCandidateCount = excluded,
                                    )
                                }
                                break
                            }
                            job.status == QualityJobStatus.FAILED -> {
                                _state.update {
                                    it.copy(
                                        step = QualityStep.FINDINGS,
                                        snackbarMessage = job.errorMessage
                                            ?: "품질 개선을 완료하지 못했어요. 다시 시도해 주세요.",
                                    )
                                }
                                break
                            }
                            else -> { /* PENDING|RUNNING: 계속 폴링 */ }
                        }
                    }
                    is ApiResult.Failure -> {
                        _state.update {
                            it.copy(
                                step = QualityStep.FINDINGS,
                                snackbarMessage = result.message,
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    // ── 후보 채택 ─────────────────────────────────────────────────────────────

    /** 후보 체크 토글(항목별 채택 선택). */
    fun toggleCandidate(candidateId: String) {
        _state.update {
            it.copy(
                candidates = it.candidates.map { c ->
                    if (c.candidateId == candidateId) c.copy(selected = !c.selected) else c
                },
            )
        }
    }

    /**
     * 선택된 후보 일괄 채택.
     * 선택이 없으면 무시한다. 성공 시 [QualityStep.ADOPTED]로 전환해 화면이 복귀 트리거를 받는다.
     */
    fun adoptSelected() {
        val current = _state.value
        val toAdopt = current.selectedCandidates.map { it.candidateId }
        val jobId = current.improvingJobId ?: return
        if (toAdopt.isEmpty()) return
        if (current.adopting) return
        _state.update { it.copy(adopting = true) }
        viewModelScope.launch {
            when (val result = qualityApi.adoptCandidates(artifactId, jobId, toAdopt)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(adopting = false, step = QualityStep.ADOPTED) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        adopting = false,
                        snackbarMessage = result.message,
                    )
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    // ── 변환 헬퍼 ─────────────────────────────────────────────────────────────

    private fun FindingDto.toUi() = FindingUi(
        findingId = findingId,
        criterionLabel = criterionLabel,
        treatmentKind = treatmentKind,
        evidenceText = evidenceText,
        suggestionMessage = suggestionGuide?.message,
        targetExperienceId = suggestionGuide?.targetExperienceId,
    )

    private fun CandidateDto.toUi() = CandidateUi(
        candidateId = candidateId,
        sectionId = sectionId,
        definitionKey = definitionKey,
        originalContent = originalContent,
        candidateContent = candidateContent,
        selected = true,
    )

    /** 처치 접수 에러 코드별 사용자 안내. */
    private fun improvementErrorMessage(code: String?, serverMessage: String?): String = when (code) {
        QUOTA_EXCEEDED_CODE ->
            "오늘 품질 개선 횟수를 모두 사용했어요. 내일 다시 시도하거나 직접 편집해 보세요."
        else -> serverMessage?.takeIf { it.isNotBlank() }
            ?: "품질 개선 요청 중 문제가 생겼어요. 다시 시도해 주세요."
    }

    companion object {
        /** 폴링 간격(기존 생성 잡 폴링과 동일 — ArtifactListViewModel.POLL_INTERVAL_MS). */
        const val POLL_INTERVAL_MS = 3_000L

        /** 일일 한도 초과 에러 코드(서버 계약). */
        const val QUOTA_EXCEEDED_CODE = "QUALITY_IMPROVEMENT_QUOTA_EXCEEDED"
    }
}
