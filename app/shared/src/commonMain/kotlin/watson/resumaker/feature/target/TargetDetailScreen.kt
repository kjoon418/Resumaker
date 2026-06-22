package watson.resumaker.feature.target

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.model.dto.WritingStrategyResponse
import watson.resumaker.model.type.StrategyStatus
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.SecondaryButton
import watson.resumaker.ui.component.SkillTag
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 목표 상세 화면. 회사·직무·채용 방향 원문과 AI 작성 전략(상태별 분기)을 열람하고,
 * "수정하기"로 편집에 진입하거나 하단 CTA로 이 목표 기반 생성에 진입한다.
 *
 * 전략 상태:
 * - PENDING/EXTRACTING → 인라인 로딩("작성 전략을 분석하는 중이에요.").
 * - READY → 구조화 표시(강조 역량·권장 어조·강조할 점·피할 점·공고 요약).
 * - FAILED → ErrorBanner + "다시 분석하기"(retryStrategy).
 */
@Composable
fun TargetDetailScreen(
    viewModel: TargetDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCreateArtifact: () -> Unit,
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
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = "목표 상세",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> LoadingState(contentModifier, caption = "불러오는 중이에요")
            state.target == null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(
                    message = state.errorMessage ?: "목표를 불러오지 못했어요.",
                    onRetry = viewModel::load,
                    title = "불러오지 못했어요",
                )
            }
            else -> DetailContent(
                target = state.target!!,
                directionExpanded = state.directionExpanded,
                contentModifier = contentModifier,
                horizontalPadding = pad,
                onToggleDirection = viewModel::toggleDirectionExpanded,
                onEdit = onEdit,
                onRetryStrategy = viewModel::retryStrategy,
                onCreateArtifact = onCreateArtifact,
            )
        }
    }
}

@Composable
private fun DetailContent(
    target: TargetResponse,
    directionExpanded: Boolean,
    contentModifier: Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onToggleDirection: () -> Unit,
    onEdit: () -> Unit,
    onRetryStrategy: () -> Unit,
    onCreateArtifact: () -> Unit,
) {
    val colors = RmTheme.colors
    Column(
        modifier = contentModifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space5),
    ) {
        // 상단: 회사·직무 제목.
        Text(
            text = targetTitle(target),
            style = RmTextStyles.titleL,
            color = colors.textPrimary,
        )

        // 채용 방향 원문(기본 3줄 clamp + 전체 보기/접기 토글).
        SectionLabel("채용 방향")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSubtle, RoundedCornerShape(RmRadius.card))
                .border(RmSize.hairline, colors.borderSubtle, RoundedCornerShape(RmRadius.card))
                .padding(RmSpacing.space4),
        ) {
            Text(
                text = target.recruitDirection,
                style = RmTextStyles.bodyMr,
                color = colors.textBody,
                maxLines = if (directionExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DirectionToggle(expanded = directionExpanded, onToggle = onToggleDirection)

        SecondaryButton(text = "수정하기", onClick = onEdit)

        // AI 작성 전략 섹션.
        SectionLabel("AI 작성 전략")
        when (target.strategyStatus) {
            StrategyStatus.PENDING, StrategyStatus.EXTRACTING -> StrategyAnalyzing()
            StrategyStatus.READY -> StrategyReady(target.writingStrategy)
            StrategyStatus.FAILED -> ErrorBanner(
                message = "네트워크 문제거나 공고 내용이 너무 짧을 수 있어요.",
                onRetry = onRetryStrategy,
                title = "전략을 분석하지 못했어요",
                retryText = "다시 분석하기",
            )
        }

        // 하단 CTA: 이 목표로 생성 진입(전략 준비 여부와 무관하게 항상 가능 — 막다른 길 금지).
        PrimaryButton(
            text = "이 목표로 이력서·포트폴리오 만들기",
            onClick = onCreateArtifact,
        )
    }
}

/** 섹션 라벨(label + textTertiary). */
@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = RmTextStyles.label, color = RmTheme.colors.textTertiary)
}

/** 채용 방향 원문 "전체 보기"/"접기" 토글(인라인 텍스트 버튼처럼 동작). */
@Composable
private fun DirectionToggle(expanded: Boolean, onToggle: () -> Unit) {
    val colors = RmTheme.colors
    Text(
        text = if (expanded) "접기" else "전체 보기",
        style = RmTextStyles.bodyS,
        color = colors.primary,
        modifier = Modifier.clickable(onClick = onToggle),
    )
}

/** PENDING/EXTRACTING — 인라인 로딩. */
@Composable
private fun StrategyAnalyzing() {
    val colors = RmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color = colors.primary,
                modifier = Modifier
                    .size(RmSize.iconMd)
                    .semantics { contentDescription = "전략 분석 중" },
            )
            Text(
                text = "작성 전략을 분석하는 중이에요.",
                style = RmTextStyles.bodyMr,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = RmSpacing.space3),
            )
        }
        Text(
            text = "잠시 후 이 화면을 다시 열면 확인할 수 있어요.",
            style = RmTextStyles.bodyS,
            color = colors.textTertiary,
        )
    }
}

/** READY — 구조화 표시. */
@Composable
private fun StrategyReady(strategy: WritingStrategyResponse?) {
    val colors = RmTheme.colors
    // writingStrategy가 (계약상) READY인데 null인 비정상 케이스 방어: 빈 전략으로 대체.
    val s = strategy ?: WritingStrategyResponse()
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space5)) {
        if (s.keywords.isNotEmpty()) {
            StrategyBlock("강조할 역량") {
                KeywordChips(s.keywords)
            }
        }
        if (s.tone.isNotBlank()) {
            StrategyBlock("권장 어조") {
                Text(text = s.tone, style = RmTextStyles.bodyS, color = colors.textPrimary)
            }
        }
        if (s.emphasize.isNotEmpty()) {
            StrategyBlock("강조할 점") {
                BulletList(items = s.emphasize, color = colors.textPrimary)
            }
        }
        if (s.avoid.isNotEmpty()) {
            StrategyBlock("피할 점") {
                BulletList(items = s.avoid, color = colors.textTertiary)
            }
        }
        if (s.summary.isNotBlank()) {
            StrategyBlock("공고 요약") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceSubtle, RoundedCornerShape(RmRadius.card))
                        .border(RmSize.hairline, colors.borderSubtle, RoundedCornerShape(RmRadius.card))
                        .padding(RmSpacing.space4),
                ) {
                    Text(text = s.summary, style = RmTextStyles.bodyMr, color = colors.textBody)
                }
            }
        }
    }
}

/** 전략 하위 블록(소제목 + 내용). */
@Composable
private fun StrategyBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
        Text(text = title, style = RmTextStyles.bodyS, color = RmTheme.colors.textSecondary)
        content()
    }
}

/** 강조 역량 키워드를 SkillTag 칩으로(최대 8 + "+N개 더"). 폭에 맞춰 FlowRow로 줄바꿈한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordChips(keywords: List<String>) {
    val shown = keywords.take(MAX_KEYWORD_CHIPS)
    val overflow = keywords.size - shown.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space2),
    ) {
        shown.forEach { keyword ->
            SkillTag(text = keyword)
        }
        if (overflow > 0) {
            Text(
                text = "+${overflow}개 더",
                style = RmTextStyles.caption,
                color = RmTheme.colors.textTertiary,
                modifier = Modifier.padding(top = RmSpacing.space1),
            )
        }
    }
}

/** 글머리(•) 목록. */
@Composable
private fun BulletList(items: List<String>, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space1)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "•", style = RmTextStyles.bodyS, color = color)
                Text(
                    text = item,
                    style = RmTextStyles.bodyS,
                    color = color,
                    modifier = Modifier.padding(start = RmSpacing.space2),
                )
            }
        }
    }
}

/** 강조 역량 칩 최대 노출 수(초과분은 "+N개 더"). */
private const val MAX_KEYWORD_CHIPS = 8
