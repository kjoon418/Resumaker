package watson.resumaker.feature.artifact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import watson.resumaker.model.dto.ArtifactSectionResponse
import watson.resumaker.model.dto.VersionHistoryResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.SectionKind
import watson.resumaker.network.ApiResult
import watson.resumaker.network.ArtifactApi

/**
 * 버전 목록의 한 버전(표시 모델). 생성순 인덱스로 사람이 읽을 라벨("버전 1"…)을 만들고, 활성 여부·생성시각을 표시한다.
 * 비교는 [sections]의 definitionKey로 수행하므로 항목은 그대로 보존한다(§363).
 */
data class VersionUi(
    val versionId: String,
    /** 생성순(1부터). 사용자에게 보이는 버전 번호. 오래된→최신 순으로 매긴다. */
    val ordinal: Int,
    val active: Boolean,
    val createdAt: String,
    val sections: List<ArtifactSectionUi>,
) {
    /** 사람이 읽을 버전 라벨("버전 3"). 활성 표시는 화면에서 배지로 따로 붙인다. */
    val label: String get() = "버전 $ordinal"
}

/**
 * definitionKey로 맞춘 비교 한 줄. 같은 항목(같은 섹션 정의)을 버전 A·B에서 대응시킨다(§363). 한쪽 버전에만 존재하는
 * 항목(키 불일치)은 반대편을 null로 둬 "이 버전엔 없음"으로 정직하게 고지한다(가짜 성공 금지).
 */
data class VersionDiffRow(
    val definitionKey: String,
    val sectionKind: SectionKind,
    val left: ArtifactSectionUi?,
    val right: ArtifactSectionUi?,
) {
    /** 양쪽 모두 존재하고 내용이 다른지(변경 강조용). 한쪽만 있으면(추가/삭제) 변경으로 본다. */
    val changed: Boolean
        get() = when {
            left == null || right == null -> true
            else -> left.content != right.content || left.status != right.status
        }
}

data class ArtifactVersionsUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val artifactId: String? = null,
    val kind: ArtifactKind? = null,
    /** 모든 버전(생성순, 오래된→최신). 화면은 보통 최신이 위로 오게 뒤집어 보여준다. */
    val versions: List<VersionUi> = emptyList(),
    val activeVersionId: String? = null,
    /** 비교 좌측(기준) 버전 id. 기본은 활성 버전. */
    val leftVersionId: String? = null,
    /** 비교 우측(대상) 버전 id. 기본은 활성 직전(없으면 활성과 동일). */
    val rightVersionId: String? = null,
    /** 복원 진행 중인 버전 id(in-flight 가드 — 중복 복원 차단·진행 표시). null이면 진행 중 없음. */
    val restoringVersionId: String? = null,
    /** 복원 성공·실패 등 일회성 안내(스낵바). 화면이 표시 후 [consumeActionMessage]로 비운다. */
    val actionMessage: String? = null,
) {
    val leftVersion: VersionUi? get() = versions.firstOrNull { it.versionId == leftVersionId }
    val rightVersion: VersionUi? get() = versions.firstOrNull { it.versionId == rightVersionId }

    /** 비교할 버전이 둘 이상 있는지(하나뿐이면 비교 UI 대신 단일 버전만 보여준다). */
    val canCompare: Boolean get() = versions.size >= 2

    /** 복원이 진행 중인지(임의 버전). 진행 중에는 다른 복원 시도를 막는다(in-flight 가드). */
    val restoreInFlight: Boolean get() = restoringVersionId != null

    /**
     * 선택된 두 버전을 definitionKey로 맞춘 비교 행 목록(§363). 좌측 버전의 항목 순서를 기준으로 정렬하고, 좌측에
     * 없고 우측에만 있는 항목은 뒤에 덧붙인다(누락 없이 모든 항목을 한 번씩 노출). 같은 키가 한 버전 안에서 중복될
     * 일은 없다(섹션 정의 1:1).
     */
    val diffRows: List<VersionDiffRow>
        get() {
            val left = leftVersion ?: return emptyList()
            val right = rightVersion
            val rightByKey = right?.sections?.associateBy { it.definitionKey } ?: emptyMap()
            val seen = mutableSetOf<String>()
            val rows = mutableListOf<VersionDiffRow>()
            left.sections.forEach { l ->
                seen += l.definitionKey
                rows += VersionDiffRow(
                    definitionKey = l.definitionKey,
                    sectionKind = l.sectionKind,
                    left = l,
                    right = rightByKey[l.definitionKey],
                )
            }
            // 좌측에 없는 우측 전용 항목(키 불일치) — "이 버전엔 없음"을 보이도록 left=null로 덧붙인다.
            right?.sections?.forEach { r ->
                if (r.definitionKey !in seen) {
                    rows += VersionDiffRow(
                        definitionKey = r.definitionKey,
                        sectionKind = r.sectionKind,
                        left = null,
                        right = r,
                    )
                }
            }
            return rows
        }
}

/**
 * 산출물 버전 기록·비교·복원 ViewModel(§271~290·§344~374). 모든 버전을 생성순으로 적재하고, 두 버전을 골라
 * definitionKey로 같은 항목을 맞춰 비교하며(§363), 한 버전을 복원(활성 전환 — §287)한다.
 *
 * 복원은 새 버전을 만들지 않고 활성만 재지정하므로(prunedVersionCount 항상 0), 성공 시 서버가 돌려준 활성 버전으로
 * 목록의 active 표시를 갱신한다. 진행 중 중복 복원은 in-flight 가드로 막는다.
 */
class ArtifactVersionsViewModel(
    private val artifactApi: ArtifactApi,
    private val artifactId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtifactVersionsUiState(artifactId = artifactId))
    val state: StateFlow<ArtifactVersionsUiState> = _state.asStateFlow()

    /** 진행 중인 목록 로드 Job. 연타 시 중복 로드 경합을 막는다. */
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            when (val result = artifactApi.getVersions(artifactId)) {
                is ApiResult.Success -> _state.value = result.value.toUiState()
                is ApiResult.Failure -> _state.update { it.copy(loading = false, errorMessage = result.message) }
            }
        }
    }

    /** 비교 좌측(기준) 버전 선택. */
    fun selectLeftVersion(versionId: String) {
        _state.update { if (it.leftVersionId == versionId) it else it.copy(leftVersionId = versionId) }
    }

    /** 비교 우측(대상) 버전 선택. */
    fun selectRightVersion(versionId: String) {
        _state.update { if (it.rightVersionId == versionId) it else it.copy(rightVersionId = versionId) }
    }

    /**
     * 버전 복원(POST .../restore). 활성 버전이면 의미가 없어 무시하고, 이미 복원 진행 중이면 무시한다(in-flight 가드).
     * 성공 시 응답(새 활성 버전)으로 목록의 active 표시·기준 버전을 갱신하고, 실패(404 등)는 일회성 안내로 알린다.
     */
    fun restoreVersion(versionId: String) {
        val current = _state.value
        if (current.restoreInFlight) return
        if (versionId == current.activeVersionId) return
        _state.update { it.copy(restoringVersionId = versionId) }
        viewModelScope.launch {
            when (val result = artifactApi.restoreVersion(artifactId, versionId)) {
                is ApiResult.Success -> {
                    val newActiveId = result.value.activeVersion.versionId
                    _state.update { s ->
                        s.copy(
                            versions = s.versions.map { it.copy(active = it.versionId == newActiveId) },
                            activeVersionId = newActiveId,
                            // 복원한 버전을 기준(좌측)으로 맞춰 갱신된 활성 내용을 바로 비교의 기준으로 둔다.
                            leftVersionId = newActiveId,
                            restoringVersionId = null,
                            actionMessage = "이 버전으로 되돌렸어요.",
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(restoringVersionId = null, actionMessage = result.message)
                }
            }
        }
    }

    /** 스낵바에 표시한 일회성 안내를 비운다(중복 표시 방지). */
    fun consumeActionMessage() {
        _state.update { if (it.actionMessage == null) it else it.copy(actionMessage = null) }
    }

    private fun watson.resumaker.model.dto.ArtifactVersionsResponse.toUiState(): ArtifactVersionsUiState {
        // 서버가 생성순(오래된→최신)으로 준다. 인덱스를 1부터 버전 번호로 매긴다.
        val ui = versions.mapIndexed { index, v -> v.toUi(ordinal = index + 1) }
        // 기본 비교: 좌측=활성 버전, 우측=생성순 직전 버전(활성이 첫 번째면 직후 버전, 없으면 활성과 동일).
        // "직전"은 생성순 목록에서 활성 바로 앞 항목으로 계산한다. 활성이 복원으로 옛 버전이어도 그 시점의 직전을
        // 정확히 가리킨다(단순 filter.lastOrNull은 목록 맨 끝을 고르므로 직전이 아닌 경우가 있음).
        val active = ui.firstOrNull { it.versionId == activeVersionId } ?: ui.lastOrNull()
        val activeIndex = if (active != null) ui.indexOf(active) else -1
        val previous = when {
            activeIndex > 0 -> ui[activeIndex - 1]          // 생성순 직전(가장 흔한 경우)
            activeIndex == 0 && ui.size > 1 -> ui[1]        // 활성이 첫 번째면 직후로 대체
            else -> active                                    // 버전이 하나뿐
        }
        return ArtifactVersionsUiState(
            loading = false,
            artifactId = artifactId,
            kind = kind,
            versions = ui,
            activeVersionId = activeVersionId,
            leftVersionId = active?.versionId,
            rightVersionId = previous?.versionId,
        )
    }

    private fun VersionHistoryResponse.toUi(ordinal: Int) = VersionUi(
        versionId = versionId,
        ordinal = ordinal,
        active = active,
        createdAt = createdAt,
        sections = sections.map { it.toUi() },
    )

    private fun ArtifactSectionResponse.toUi() = ArtifactSectionUi(
        id = id,
        sectionKind = sectionKind,
        definitionKey = definitionKey,
        content = content,
        status = status,
        sourceExperienceIds = sourceExperienceIds,
    )
}
