package watson.resumaker.feature.artifact.quality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
    /** 이 소견이 가리키는 생성 항목. 처치는 항목(섹션) 단위로 1후보를 만들므로 제외 수 계산에 쓴다. */
    val sectionId: String,
    val criterionLabel: String,
    val treatmentKind: TreatmentKind,
    val evidenceText: String?,
    val suggestionMessage: String?,
    /** SUGGESTION이고 특정 경험으로 이동할 때 non-null. null이면 경험 목록으로. */
    val targetExperienceId: String?,
)

/**
 * 소견이 달린 한 항목(섹션)의 표시 맥락. 소견을 이 항목에 정박(anchor)시켜, "어느 항목의, 어떤 내용이"를 보여준다.
 */
data class ReviewedSectionUi(
    val sectionId: String,
    /** 항목 이름(열람 화면 항목 제목과 동일한 키). */
    val definitionKey: String,
    /** 항목의 현재 내용(사용자가 "내 이력서의 이 부분"임을 알아보는 정박점). */
    val content: String,
)

/** 한 항목과 그 항목에 달린 소견들의 묶음(화면이 항목 카드로 렌더한다). */
data class SectionFindingsUi(
    val section: ReviewedSectionUi,
    val findings: List<FindingUi>,
) {
    /** 이 항목의 자동 다듬기 대상 소견(체크박스 표시·선택 단위). */
    val autoRewriteFindings: List<FindingUi> get() = findings.filter { it.treatmentKind == TreatmentKind.AUTO_REWRITE }

    /** 이 항목의 경험 보강 안내 소견. */
    val suggestionFindings: List<FindingUi> get() = findings.filter { it.treatmentKind == TreatmentKind.SUGGESTION }
}

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
 * IDLE → REVIEWING(점검 중) → FINDINGS(소견 표시) → [submitImprovement로 비차단 접수 후 산출물 열람 화면 복귀]
 * 또는 재진입(resume)으로 곧장 CANDIDATES(비교·채택) → ADOPTED(채택 완료).
 * 어느 단계에서든 에러가 발생하면 [errorMessage]가 세팅된다.
 *
 * 개선 작업 폴링은 더 이상 이 화면이 아니라 산출물 열람 화면([watson.resumaker.feature.artifact.ArtifactViewModel])이
 * 비차단 진행 카드로 담당한다(§3 — '이대로 다듬기' 후 빈 화면 대기 해소).
 */
enum class QualityStep {
    /** 초기(진입 직전). */
    IDLE,
    /** 품질 점검 API 호출 중(스켈레톤 로딩). 재진입(resume) 시 후보 적재 중에도 잠시 쓰인다. */
    REVIEWING,
    /** 소견 목록 표시(소견 0건 포함). */
    FINDINGS,
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
    /** 소견이 달린 항목들(이름·내용). 소견을 항목별로 묶어 정박해 보여주는 데 쓴다(중복처럼 보이던 문제 해소). */
    val reviewedSections: List<ReviewedSectionUi> = emptyList(),
    /** 사용자가 선택한 AUTO_REWRITE 소견 id 집합(FINDINGS 단계). */
    val selectedFindingIds: Set<String> = emptySet(),
    /** 처치 접수(submitImprovement) 진행 중(버튼 로딩 표시). */
    val submitting: Boolean = false,
    /**
     * 처치 접수(202) 성공 시 만들어진 작업 id(일회성 복귀 트리거). 화면이 이 값을 보고 산출물 열람 화면으로 복귀하고
     * [consumeSubmitted]로 비운다(§3 비차단 — 빈 화면 대기 없이 열람 화면의 진행 카드로 이어진다).
     */
    val submittedJobId: String? = null,
    /** 채택 대상 작업 id(CANDIDATES 단계 — 재진입 시 적재한 jobId). adopt 요청 키. */
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

    /**
     * 소견을 항목별로 묶은 렌더 모델(활성 버전 항목 순서 유지, 소견 없는 항목 제외). 화면은 항목 이름·내용 카드
     * 아래에 그 항목의 소견만 보여줘 "어느 항목의 어느 부분이 문제인지"를 정박한다(중복 오해 해소).
     */
    val sectionFindings: List<SectionFindingsUi>
        get() = reviewedSections
            .map { section -> SectionFindingsUi(section, findings.filter { it.sectionId == section.sectionId }) }
            .filter { it.findings.isNotEmpty() }

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
    /**
     * non-null이면 점검을 건너뛰고 이 작업의 후보 비교·채택(2단계)으로 곧장 진입한다(비차단 개선 §3 — 산출물 열람
     * 화면의 "확인하기" 재진입). null이면 정상 점검 흐름이다.
     */
    private val resumeJobId: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(QualityReviewUiState())
    val state: StateFlow<QualityReviewUiState> = _state.asStateFlow()

    /** 진행 중인 점검 Job. 연타 방지. */
    private var reviewJob: Job? = null

    /** 비차단 개선의 후보 재진입 모드 여부(이 경우 화면은 점검을 자동 시작하지 않는다). */
    val isResuming: Boolean get() = resumeJobId != null

    init {
        // 재진입 모드면 점검을 건너뛰고 곧장 후보를 적재한다(2단계 직행 — 산출물 열람 화면의 "확인하기").
        if (resumeJobId != null) resumeCandidates(resumeJobId)
    }

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
                    val sections = response.sections.map {
                        ReviewedSectionUi(sectionId = it.sectionId, definitionKey = it.definitionKey, content = it.content)
                    }
                    _state.update {
                        it.copy(
                            step = QualityStep.FINDINGS,
                            findings = findings,
                            reviewedSections = sections,
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
     * "이대로 다듬기" 클릭 — 선택된 AUTO_REWRITE 소견으로 개선 작업을 **비차단 접수**한다(§3). 접수(202)되면 폴링하지
     * 않고 산출물 열람 화면으로 돌아간다(빈 화면 대기 해소). 화면이 [submittedJobId]를 보고 복귀를 트리거하고, 열람
     * 화면이 진행 카드로 완료까지 폴링한다. 선택이 없거나 이미 접수 중이면 무시한다.
     */
    fun submitImprovement() {
        val current = _state.value
        if (!current.canSubmitImprovement) return
        if (current.submitting) return
        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val findingIds = current.selectedFindingIds.toList()
            when (val result = qualityApi.submitImprovement(artifactId, findingIds)) {
                is ApiResult.Success -> _state.update {
                    it.copy(submitting = false, submittedJobId = result.value.jobId)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        snackbarMessage = improvementErrorMessage(result.code, result.message),
                    )
                }
            }
        }
    }

    /** 복귀 트리거(submittedJobId)를 소비한다(중복 복귀 방지). 화면이 복귀 내비를 호출한 뒤 비운다. */
    fun consumeSubmitted() = _state.update { if (it.submittedJobId == null) it else it.copy(submittedJobId = null) }

    // ── 후보 재진입(비차단 개선 §3) ──────────────────────────────────────────────

    /**
     * 준비된 개선 작업의 후보를 적재해 곧장 비교·채택(2단계)으로 들어간다. 비차단 개선에서 산출물 열람 화면의
     * "확인하기"가 이 ViewModel을 resumeJobId로 만들어 진입시킨다. 아직 준비 전이거나 실패·조회 실패면 점검 화면
     * (FINDINGS)으로 안내한다(막다른 길 금지). 제외 항목 수는 서버 계산값([QualityImprovementJobResponse.excludedSectionCount])을
     * 그대로 쓴다(클라이언트가 원래 선택을 기억하지 못해도 정확 — 가짜 성공 금지).
     */
    private fun resumeCandidates(jobId: String) {
        _state.update { it.copy(step = QualityStep.REVIEWING, errorMessage = null) }
        viewModelScope.launch {
            when (val result = qualityApi.getImprovementJob(artifactId, jobId)) {
                is ApiResult.Success -> {
                    val job = result.value
                    if (job.status == QualityJobStatus.SUCCEEDED) {
                        _state.update {
                            it.copy(
                                step = QualityStep.CANDIDATES,
                                candidates = job.candidates.orEmpty().map { c -> c.toUi() },
                                improvingJobId = jobId,
                                excludedCandidateCount = job.excludedSectionCount,
                            )
                        }
                    } else {
                        // 준비 전/실패 — 점검 화면으로 돌려보내 다시 시도하도록 한다(ErrorBanner 재시도).
                        _state.update {
                            it.copy(
                                step = QualityStep.FINDINGS,
                                errorMessage = job.errorMessage
                                    ?: "개선 결과를 아직 불러올 수 없어요. 잠시 후 다시 시도해 주세요.",
                            )
                        }
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(step = QualityStep.FINDINGS, errorMessage = result.message)
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
        sectionId = sectionId,
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
        /** 일일 한도 초과 에러 코드(서버 계약). */
        const val QUOTA_EXCEEDED_CODE = "QUALITY_IMPROVEMENT_QUOTA_EXCEEDED"
    }
}
