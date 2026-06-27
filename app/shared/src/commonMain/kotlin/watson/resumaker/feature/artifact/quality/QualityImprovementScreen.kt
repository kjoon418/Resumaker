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
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 품질 개선 화면(단계 2/2 — 원본↔개선 비교·채택).
 *
 * 개선 후보를 항목별로 원본↔개선 비교로 보여주고, 사용자가 원하는 후보를 선택해 일괄 채택한다.
 * "One Thing per Page": 이 화면은 비교·채택만 담당한다(진단은 앞 화면에서 완료).
 *
 * [viewModel]은 [QualityReviewScreen]과 공유한 인스턴스를 전달한다(상태 공유).
 */
@Composable
fun QualityImprovementScreen(
    viewModel: QualityReviewViewModel,
    onBack: () -> Unit,
    /** 채택 성공 후 산출물 열람 화면으로 복귀. */
    onAdopted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 스낵바(채택 실패 안내 등).
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    // 채택 완료(ADOPTED): 산출물 열람으로 복귀.
    LaunchedEffect(state.step) {
        if (state.step == QualityStep.ADOPTED) {
            onAdopted()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = "개선 결과 확인",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()

        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pad)
                .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
            verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
        ) {
            if (state.allCandidatesExcluded) {
                // 후보가 전부 제외됨 — 채택할 게 없으니 비활성 버튼만 남기지 않고 직접 편집 출구를 준다(UX-10).
                InfoCard(icon = RmIcons.Info, title = "이번엔 다듬을 항목이 없었어요") {
                    Text(
                        text = "안전하게 다듬을 수 있는 항목을 찾지 못했어요. 원본은 그대로 두었으니 직접 편집해 고쳐 보세요.",
                        style = RmTextStyles.bodyS,
                        color = RmTheme.colors.onPrimaryContainer,
                    )
                }
                PrimaryButton(
                    text = "직접 편집하러 가기",
                    onClick = onAdopted,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                )
            } else {
                // 검증 실패로 제외된 후보가 있으면 고지(가짜 성공 금지 — 정직성 원칙).
                if (state.excludedCandidateCount > 0) {
                    InfoCard(icon = RmIcons.Info, title = "일부 항목은 유지했어요") {
                        Text(
                            text = "이 부분은 안전하게 다듬기 어려워 원본을 유지했어요. " +
                                "직접 편집하거나 다시 시도해 보세요.",
                            style = RmTextStyles.bodyS,
                            color = RmTheme.colors.onPrimaryContainer,
                        )
                    }
                }

                Text(
                    text = "개선된 항목을 원하는 대로 선택해 채택하세요. 선택하지 않은 항목은 그대로 유지돼요.",
                    style = RmTextStyles.bodyS,
                    color = RmTheme.colors.textSecondary,
                )

                // 후보 비교 카드 목록.
                state.candidates.forEach { candidate ->
                    CandidateCompareCard(
                        candidate = candidate,
                        onToggle = { viewModel.toggleCandidate(candidate.candidateId) },
                    )
                }

                // 일괄 채택 버튼: 선택된 후보가 없으면 비활성.
                PrimaryButton(
                    text = if (state.selectedCandidates.isEmpty()) "채택할 항목 선택" else "선택한 ${state.selectedCandidates.size}건 채택하기",
                    onClick = viewModel::adoptSelected,
                    enabled = state.selectedCandidates.isNotEmpty(),
                    loading = state.adopting,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 원본↔개선 비교 카드.
 * 체크박스로 채택 여부를 선택하고, 원본과 개선 내용을 위아래로 대조한다.
 * 채택 기본값은 true(전부 채택 권장)이고 사용자가 개별 해제할 수 있다.
 */
@Composable
private fun CandidateCompareCard(
    candidate: CandidateUi,
    onToggle: () -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
            // 헤더: 항목 키 + 채택 체크박스.
            Row(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = candidate.definitionKey,
                    style = RmTextStyles.label,
                    color = colors.textPrimary,
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                )
                Checkbox(
                    checked = candidate.selected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.primary,
                        uncheckedColor = colors.textTertiary,
                    ),
                )
            }

            // 원본.
            CompareBlock(label = "원본", content = candidate.originalContent, labelColor = colors.textTertiary)

            // 개선.
            CompareBlock(label = "개선", content = candidate.candidateContent, labelColor = colors.success)
        }
    }
}

@Composable
private fun CompareBlock(
    label: String,
    content: String,
    labelColor: androidx.compose.ui.graphics.Color,
) {
    val colors = RmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space1)) {
        Text(text = label, style = RmTextStyles.caption, color = labelColor)
        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(RmSpacing.space2),
        ) {
            Text(
                text = content,
                style = RmTextStyles.bodyS,
                color = colors.textBody,
            )
        }
    }
}
