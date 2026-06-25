package watson.resumaker.feature.artifact

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.ArtifactSummaryResponse
import watson.resumaker.model.dto.GenerationJobResponse
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.GenerationJobRetryMode
import watson.resumaker.model.type.GenerationJobStatus
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.Badge
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.GhostButton
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.TextLink
import watson.resumaker.ui.component.headerWidthForTab
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 내 산출물 목록 화면. 진행 중/실패 생성 작업 카드(상단)와 완성 산출물 카드(하단)를 함께 보여주고, 활성 작업이
 * 있는 동안 ViewModel이 폴링해 완료를 반영한다(비동기 생성 전환). 빈 상태면 만들기 진입을 유도한다.
 */
@Composable
fun ArtifactListScreen(
    viewModel: ArtifactListViewModel,
    /** non-null이면 탭 목적지(switchRoot)로 진입한 것이므로 AppHeader(탭)를, null이면 PageHeader(뒤로가기)를 그린다. */
    selectedTab: HeaderTab? = null,
    onBack: () -> Unit,
    onSelectTab: (HeaderTab) -> Unit = {},
    onOpenMyPage: () -> Unit = {},
    /** 완성 산출물 카드 클릭 → 열람(Screen.ArtifactView). */
    onOpenArtifact: (String) -> Unit,
    /** 빈 상태 "만들기 시작" → 생성 화면(Screen.Artifact). */
    onCreate: () -> Unit,
    /**
     * 입력 관련 실패 카드 "경험 다시 고르기"(EDIT_INPUTS) → 입력을 미리 채운 생성 화면. 일시적 실패(IN_PLACE)는
     * 화면 이동 없이 ViewModel이 그 자리에서 재요청하므로 콜백이 필요 없다.
     */
    onEditInputs: (GenerationJobResponse) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.WIDE,
        headerWidth = headerWidthForTab(selectedTab), // 탭 목적지 헤더 폭 정책(공유 크롬 일치)
        header = { windowSize ->
            if (selectedTab != null) {
                AppHeader(
                    selected = selectedTab,
                    onSelectTab = onSelectTab,
                    onOpenAccount = onOpenMyPage,
                    windowSize = windowSize,
                    horizontalPadding = windowSize.pagePadding(),
                )
            } else {
                PageHeader(
                    title = "내 이력서·포트폴리오",
                    horizontalPadding = windowSize.pagePadding(),
                    onBack = onBack,
                )
            }
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                SkeletonList(showLeadingChip = false)
            }
            state.isEmpty -> Box(
                modifier = contentModifier.fillMaxWidth().padding(horizontal = pad).padding(top = RmSpacing.space8),
                contentAlignment = Alignment.TopCenter,
            ) {
                EmptyState(
                    icon = RmIcons.Note,
                    title = "아직 만든 이력서·포트폴리오가 없어요",
                    description = "경험과 목표를 바탕으로 지금 바로 만들어 보세요.",
                    actionText = "만들기 시작",
                    onAction = onCreate,
                )
            }
            else -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
            ) {
                // 진행 중/실패 작업(상단, 최신순). 완료된 작업은 ViewModel이 제외해 산출물로만 보인다.
                state.renderJobs.forEach { job ->
                    ArtifactJobCard(
                        job = job,
                        deleting = job.jobId in state.deletingJobIds,
                        retrying = job.jobId in state.retryingJobIds,
                        onRetryInPlace = { viewModel.retryJob(job) },
                        onEditInputs = { onEditInputs(job) },
                        onDelete = { viewModel.deleteJob(job) },
                    )
                }
                // 완성 산출물(하단, 최신순).
                state.artifacts.forEach { artifact ->
                    ArtifactSummaryCard(
                        artifact = artifact,
                        onClick = { onOpenArtifact(artifact.id) },
                    )
                }
            }
        }
    }
}

/**
 * 생성 작업 카드(상태별). PENDING/RUNNING은 진행 표시(클릭 불가), FAILED는 클릭 시 인라인 오류 배너를 펼쳐
 * 다시 만들기 액션(서버 [GenerationJobResponse.retryMode] 분류)·"기록 삭제"를 제공한다. SUCCEEDED는 이 카드로
 * 렌더하지 않는다(완성 산출물 카드로 표시).
 *
 * 다시 만들기 액션은 retryMode로 갈린다: IN_PLACE면 "다시 만들기"(그 자리에서 저장된 입력으로 재요청),
 * EDIT_INPUTS면 "경험 다시 고르기"(입력 프리필 제작 화면), NONE이면 액션 없음(한도 초과 등).
 */
@Composable
private fun ArtifactJobCard(
    job: GenerationJobResponse,
    deleting: Boolean,
    retrying: Boolean,
    onRetryInPlace: () -> Unit,
    onEditInputs: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = RmTheme.colors
    var expanded by remember(job.jobId) { mutableStateOf(false) }
    val failed = job.status == GenerationJobStatus.FAILED

    RmCard(
        // 실패 카드만 클릭으로 오류 상세를 펼친다. 진행 중 카드는 클릭 불가.
        onClick = if (failed) ({ expanded = !expanded }) else null,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 진행 중이면 좌측에 작은 스피너 칩.
                if (job.status.isActive) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        strokeWidth = RmSpacing.space0_5,
                        modifier = Modifier
                            .padding(end = RmSpacing.space3)
                            .size(RmSize.spinnerSm),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    JobStatusBadge(job.status)
                    Text(
                        text = jobTitle(job.kind, job.targetCompany),
                        style = RmTextStyles.bodyM,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = RmSpacing.space1),
                    )
                    Text(
                        text = jobMeta(job.status),
                        style = RmTextStyles.caption,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = RmSpacing.space1),
                    )
                }
            }

            // RUNNING: 카드 하단 얇은 진행 라인(불확정 진행 느낌).
            if (job.status == GenerationJobStatus.RUNNING) {
                IndeterminateProgressLine(
                    modifier = Modifier.padding(top = RmSpacing.space3),
                )
            }

            // FAILED + 펼침: 인라인 오류 + 액션.
            if (failed && expanded) {
                Column(
                    modifier = Modifier.padding(top = RmSpacing.space3),
                    verticalArrangement = Arrangement.spacedBy(RmSpacing.space2),
                ) {
                    Text(
                        text = failureMessage(job.errorCode, job.errorMessage),
                        style = RmTextStyles.bodyS,
                        color = colors.textSecondary,
                    )
                    // 다시 만들기 동작은 서버 분류(retryMode)로 갈린다(한도 초과 NONE은 버튼 없음).
                    when (job.retryMode) {
                        GenerationJobRetryMode.IN_PLACE -> GhostButton(
                            text = if (retrying) "다시 만드는 중…" else "다시 만들기",
                            onClick = { if (!retrying) onRetryInPlace() },
                        )
                        GenerationJobRetryMode.EDIT_INPUTS -> GhostButton(
                            text = "경험 다시 고르기",
                            onClick = onEditInputs,
                        )
                        GenerationJobRetryMode.NONE -> Unit
                    }
                    TextLink(
                        text = if (deleting) "삭제하는 중…" else "기록 삭제",
                        onClick = { if (!deleting) onDelete() },
                    )
                }
            }
        }
    }
}

/** 완성 산출물 카드. 클릭으로 열람(chevron). */
@Composable
private fun ArtifactSummaryCard(
    artifact: ArtifactSummaryResponse,
    onClick: () -> Unit,
) {
    val colors = RmTheme.colors
    RmCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Badge(text = "완성", fg = colors.success, bg = colors.successBg)
                Text(
                    text = jobTitle(artifact.kind, artifact.targetCompany),
                    style = RmTextStyles.bodyM,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = RmSpacing.space1),
                )
            }
            Icon(
                imageVector = RmIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(RmSize.iconMd),
            )
        }
    }
}

@Composable
private fun JobStatusBadge(status: GenerationJobStatus) {
    val colors = RmTheme.colors
    when (status) {
        GenerationJobStatus.PENDING -> Badge(text = "대기 중", fg = colors.warning, bg = colors.warningBg)
        GenerationJobStatus.RUNNING -> Badge(text = "생성 중", fg = colors.primary, bg = colors.primaryContainer)
        GenerationJobStatus.FAILED -> Badge(text = "생성 실패", fg = colors.danger, bg = colors.dangerBg)
        // SUCCEEDED는 이 카드로 렌더하지 않으나, when 망라성을 위해 완성 배지를 둔다.
        GenerationJobStatus.SUCCEEDED -> Badge(text = "완성", fg = colors.success, bg = colors.successBg)
    }
}

/** RUNNING 카드 하단 불확정 진행 라인. shimmer와 동일하게 infiniteRepeatable tween reverse로 폭을 보간한다. */
@Composable
private fun IndeterminateProgressLine(modifier: Modifier = Modifier) {
    val colors = RmTheme.colors
    val transition = rememberInfiniteTransition(label = "jobProgress")
    val fraction by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "jobProgressFraction",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.hairline + RmSpacing.space0_5)
            .background(colors.borderSubtle, RoundedCornerShape(RmRadius.full)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(RmSize.hairline + RmSpacing.space0_5)
                .background(colors.primary, RoundedCornerShape(RmRadius.full)),
        )
    }
}

/** 작업 종류·회사로 카드 제목을 만든다. 회사가 없으면 종류만. */
internal fun jobTitle(kind: ArtifactKind, targetCompany: String?): String {
    val noun = if (kind == ArtifactKind.RESUME) "이력서" else "포트폴리오"
    val company = targetCompany?.takeIf { it.isNotBlank() }
    return if (company != null) "$company $noun" else noun
}

/** 상태별 보조 메타 문구. */
private fun jobMeta(status: GenerationJobStatus): String = when (status) {
    GenerationJobStatus.PENDING -> "생성 준비 중이에요"
    GenerationJobStatus.RUNNING -> "AI가 작성하고 있어요"
    GenerationJobStatus.FAILED -> "생성하지 못했어요"
    GenerationJobStatus.SUCCEEDED -> "완성됐어요"
}

/** 실패 코드별 사용자 안내(디자이너 확정). 코드가 없거나 미지정이면 errorMessage 또는 기본 문구. */
internal fun failureMessage(errorCode: String?, errorMessage: String?): String = when (errorCode) {
    ERROR_AI_UNAVAILABLE -> "지금은 AI 생성을 사용할 수 없어요. 잠시 후 다시 시도해 주세요."
    ERROR_NO_CONTENT -> "생성할 수 있는 항목이 없었어요. 관련 경험을 추가하거나 다른 경험을 골라 주세요."
    ERROR_SOURCE_MISSING -> "생성에 쓸 경험이나 목표를 찾을 수 없어요."
    ERROR_QUOTA_EXCEEDED -> "오늘 생성 한도를 모두 사용했어요. 내일 다시 시도해 주세요."
    else -> errorMessage?.takeIf { it.isNotBlank() } ?: "생성 중 문제가 생겼어요. 다시 시도해 주세요."
}

private const val ERROR_AI_UNAVAILABLE = "AI_GENERATION_UNAVAILABLE"
private const val ERROR_NO_CONTENT = "GENERATION_NO_CONTENT"
private const val ERROR_SOURCE_MISSING = "GENERATION_SOURCE_MISSING"
private const val ERROR_QUOTA_EXCEEDED = "GENERATION_QUOTA_EXCEEDED"
