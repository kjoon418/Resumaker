package watson.resumaker.feature.artifact.quality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.type.TreatmentKind
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.GhostButton
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 품질 점검 화면(단계 1/2 — 소견 확인). "One Thing per Page": 진단 결과를 먼저 보여주고,
 * 사용자가 원하는 소견만 선택해 "이대로 다듬기"로 다음 단계(개선 처리)로 이동한다.
 *
 * RESUME 전용이다(QC10). 화면 진입 시 진단을 자동으로 시작한다.
 */
@Composable
fun QualityReviewScreen(
    viewModel: QualityReviewViewModel,
    onBack: () -> Unit,
    /** 개선 작업 완료 후 후보 비교 화면(2단계)으로 이동. ViewModel을 공유하므로 상위에서 같은 인스턴스 전달. */
    onProceedToImprovement: () -> Unit,
    /** "경험 보강하러 가기" 클릭 — 특정 경험 수정 or 경험 목록으로 이동. */
    onOpenExperience: (experienceId: String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 스낵바(한도 초과·에러 등) 일회성 표시.
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    // 개선 처리가 완료(CANDIDATES 단계)되면 다음 화면으로 이동.
    LaunchedEffect(state.step) {
        if (state.step == QualityStep.CANDIDATES) {
            onProceedToImprovement()
        }
    }

    // 진입 시 자동으로 진단 시작.
    LaunchedEffect(Unit) {
        viewModel.startReview()
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = "품질 점검",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()

        when (state.step) {
            QualityStep.IDLE, QualityStep.REVIEWING -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                SkeletonList(showLeadingChip = false)
            }

            QualityStep.FINDINGS -> {
                if (state.errorMessage != null) {
                    Box(contentModifier.padding(pad)) {
                        ErrorBanner(
                            message = state.errorMessage!!,
                            onRetry = viewModel::startReview,
                        )
                    }
                } else if (state.hasNoFindings) {
                    // 긍정 빈 상태: 부족함이 아닌 칭찬으로 표현(UX 핵심 가이드 — 긍정 프레이밍).
                    Box(contentModifier.padding(pad)) {
                        EmptyState(
                            icon = RmIcons.CheckCircle,
                            title = "지금도 충분히 좋아요",
                            description = "자동으로 개선할 부분을 찾지 못했어요. 계속 잘 쓰고 계세요!",
                            actionText = "돌아가기",
                            onAction = onBack,
                        )
                    }
                } else {
                    FindingsContent(
                        modifier = contentModifier,
                        pad = pad,
                        state = state,
                        onToggleFinding = viewModel::toggleFinding,
                        onSubmitImprovement = viewModel::submitImprovement,
                        onOpenExperience = onOpenExperience,
                    )
                }
            }

            // IMPROVING: 개선 작업 폴링 중(스켈레톤 + 안내 문구).
            QualityStep.IMPROVING -> Column(
                modifier = contentModifier
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
            ) {
                InfoCard(icon = RmIcons.Sparkles, title = "이력서를 다듬는 중이에요") {
                    Text(
                        text = "AI가 선택한 항목을 꼼꼼히 다듬고 있어요. 잠시 기다려 주세요.",
                        style = RmTextStyles.bodyS,
                        color = RmTheme.colors.onPrimaryContainer,
                    )
                }
                SkeletonList(showLeadingChip = false)
            }

            // CANDIDATES, ADOPTED: 이미 다음 화면으로 전환됐거나 전환 중. 빈 껍데기로 깜박임 방지.
            QualityStep.CANDIDATES, QualityStep.ADOPTED -> Box(contentModifier) {}
        }
    }
}

/**
 * 소견 목록 본문: **항목별로 묶은** 카드(항목 이름 + 실제 내용 + 그 항목의 소견들) + 처치 버튼.
 *
 * 같은 기준(예: "약한 동사")이 여러 항목에 걸쳐도, 각 소견이 자기 항목 이름·내용 아래에 정박되므로 중복/오류처럼
 * 보이지 않고 "어느 항목의 어느 부분이 어떻게 문제인지"가 분명해진다.
 */
@Composable
private fun FindingsContent(
    modifier: androidx.compose.ui.Modifier,
    pad: androidx.compose.ui.unit.Dp,
    state: QualityReviewUiState,
    onToggleFinding: (String) -> Unit,
    onSubmitImprovement: () -> Unit,
    onOpenExperience: (String?) -> Unit,
) {
    val colors = RmTheme.colors
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pad)
            .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
    ) {
        Text(
            text = "항목별로 다듬을 부분을 찾았어요. 체크한 부분은 사실을 바꾸지 않고 표현만 다듬어요.",
            style = RmTextStyles.bodyS,
            color = colors.textSecondary,
        )

        state.sectionFindings.forEach { group ->
            SectionFindingCard(
                group = group,
                selectedFindingIds = state.selectedFindingIds,
                onToggleFinding = onToggleFinding,
                onOpenExperience = onOpenExperience,
            )
        }

        // 자동으로 다듬을 소견이 하나라도 있을 때만 처치 버튼을 노출한다(선택이 없으면 비활성).
        if (state.autoRewriteFindings.isNotEmpty()) {
            PrimaryButton(
                text = "이대로 다듬기",
                onClick = onSubmitImprovement,
                enabled = state.canSubmitImprovement,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 한 항목 카드: 항목 이름 + 현재 내용(정박점) + 그 항목의 소견들. AUTO_REWRITE 소견은 체크박스로 선택하고,
 * SUGGESTION 소견은 "경험 보강하러 가기"로 유도한다(텍스트 변경 없음).
 */
@Composable
private fun SectionFindingCard(
    group: SectionFindingsUi,
    selectedFindingIds: Set<String>,
    onToggleFinding: (String) -> Unit,
    onOpenExperience: (String?) -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
            // 항목 이름(어느 부분인지).
            Text(
                text = group.section.definitionKey,
                style = RmTextStyles.label,
                color = colors.textPrimary,
            )
            // 항목의 현재 내용(사용자가 "내 이력서의 이 부분"임을 알아보는 정박점).
            Text(
                text = group.section.content,
                style = RmTextStyles.bodyS,
                color = colors.textSecondary,
            )

            // 자동 다듬기 소견: 체크박스로 선택.
            group.autoRewriteFindings.forEach { finding ->
                AutoRewriteFindingRow(
                    finding = finding,
                    checked = finding.findingId in selectedFindingIds,
                    onToggle = { onToggleFinding(finding.findingId) },
                )
            }
            // 경험 보강 소견: 텍스트 변경 없이 보강 안내.
            group.suggestionFindings.forEach { finding ->
                SuggestionFindingRow(finding = finding, onOpenExperience = onOpenExperience)
            }
        }
    }
}

/** AUTO_REWRITE 소견 한 줄: 체크박스 + 기준 라벨 + 근거 텍스트(있으면 — 어떤 표현이 문제인지). */
@Composable
private fun AutoRewriteFindingRow(
    finding: FindingUi,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = RmTheme.colors
    Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                uncheckedColor = colors.textTertiary,
            ),
        )
        Column(
            modifier = androidx.compose.ui.Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RmSpacing.space1),
        ) {
            Text(text = finding.criterionLabel, style = RmTextStyles.bodyM, color = colors.textPrimary)
            finding.evidenceText?.let { evidence ->
                Text(text = "“$evidence”", style = RmTextStyles.bodyS, color = colors.textSecondary)
            }
        }
    }
}

/** SUGGESTION 소견 한 줄: 기준 라벨 + 보강 안내 + "경험 보강하러 가기"(텍스트는 바꾸지 않는다). */
@Composable
private fun SuggestionFindingRow(
    finding: FindingUi,
    onOpenExperience: (String?) -> Unit,
) {
    val colors = RmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space1)) {
        Text(text = finding.criterionLabel, style = RmTextStyles.bodyM, color = colors.textPrimary)
        finding.suggestionMessage?.let { msg ->
            Text(text = msg, style = RmTextStyles.bodyS, color = colors.textSecondary)
        }
        GhostButton(
            text = "경험 보강하러 가기",
            onClick = { onOpenExperience(finding.targetExperienceId) },
        )
    }
}
