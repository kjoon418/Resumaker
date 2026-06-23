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

/** 소견 목록 본문: AUTO_REWRITE 체크박스 섹션 + SUGGESTION 안내 섹션 + 처치 버튼. */
@Composable
private fun FindingsContent(
    modifier: androidx.compose.ui.Modifier,
    pad: androidx.compose.ui.unit.Dp,
    state: QualityReviewUiState,
    onToggleFinding: (String) -> Unit,
    onSubmitImprovement: () -> Unit,
    onOpenExperience: (String?) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = pad)
            .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
    ) {
        // AUTO_REWRITE 섹션: 체크로 선택해 "이대로 다듬기" 처치.
        if (state.autoRewriteFindings.isNotEmpty()) {
            FindingSectionHeader(
                title = "이렇게 다듬을 수 있어요",
                description = "체크한 항목을 사실을 바꾸지 않고 표현만 다듬어요.",
            )
            state.autoRewriteFindings.forEach { finding ->
                AutoRewriteFindingCard(
                    finding = finding,
                    checked = finding.findingId in state.selectedFindingIds,
                    onToggle = { onToggleFinding(finding.findingId) },
                )
            }
            PrimaryButton(
                text = "이대로 다듬기",
                onClick = onSubmitImprovement,
                enabled = state.canSubmitImprovement,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }

        // SUGGESTION 섹션: 경험 보강 안내(텍스트 변경 없음).
        if (state.suggestionFindings.isNotEmpty()) {
            FindingSectionHeader(
                title = "경험을 보강하면 더 강해져요",
                description = "아래 항목은 경험에 내용을 추가해야 개선할 수 있어요.",
            )
            state.suggestionFindings.forEach { finding ->
                SuggestionFindingCard(
                    finding = finding,
                    onOpenExperience = onOpenExperience,
                )
            }
        }
    }
}

@Composable
private fun FindingSectionHeader(title: String, description: String) {
    val colors = RmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space1)) {
        Text(text = title, style = RmTextStyles.headingS, color = colors.textPrimary)
        Text(text = description, style = RmTextStyles.bodyS, color = colors.textSecondary)
    }
}

/**
 * AUTO_REWRITE 소견 카드: 체크박스 + 기준 라벨 + 근거 텍스트(있으면).
 * 사용자가 어떤 항목이 개선될지 파악하고 동의 여부를 선택할 수 있다.
 */
@Composable
private fun AutoRewriteFindingCard(
    finding: FindingUi,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
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
                Text(
                    text = finding.criterionLabel,
                    style = RmTextStyles.label,
                    color = colors.textPrimary,
                )
                finding.evidenceText?.let { evidence ->
                    Text(
                        text = evidence,
                        style = RmTextStyles.bodyS,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * SUGGESTION 소견 카드: 안내 메시지 + "경험 보강하러 가기" 버튼.
 * 텍스트를 직접 바꾸지 않고 사용자가 경험 내용을 보강해 오도록 유도한다.
 */
@Composable
private fun SuggestionFindingCard(
    finding: FindingUi,
    onOpenExperience: (String?) -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
            Text(
                text = finding.criterionLabel,
                style = RmTextStyles.label,
                color = colors.textPrimary,
            )
            finding.suggestionMessage?.let { msg ->
                Text(
                    text = msg,
                    style = RmTextStyles.bodyS,
                    color = colors.textSecondary,
                )
            }
            GhostButton(
                text = "경험 보강하러 가기",
                onClick = { onOpenExperience(finding.targetExperienceId) },
            )
        }
    }
}
