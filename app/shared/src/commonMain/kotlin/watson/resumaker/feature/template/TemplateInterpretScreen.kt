package watson.resumaker.feature.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.SectionResponse
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.SecondaryButton
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 회사 양식 붙여넣기 + 확정 게이트 화면(FU-C, 도메인 이해 §2.5).
 *
 * 단계:
 * 1. [InterpretGateState.Idle]: 텍스트 붙여넣기 입력 + "해석" 버튼.
 * 2. [InterpretGateState.Interpreting]: 로딩.
 * 3. [InterpretGateState.Gate]: 추출된 섹션 읽기 전용 표시 + 이름 입력 + "이대로 만들기" / "처음으로".
 * 4. [InterpretGateState.Fallback]: "양식을 알아보기 어려워요." + 폴백 버튼 2개.
 * 5. [InterpretGateState.Confirmed]: 화면이 감지해 편집 화면으로 이동.
 *
 * IA 검토 필요: 확정 게이트의 읽기 전용 섹션 표시 밀도·폴백 버튼 배치는 최종 디자이너 리뷰 대상이다.
 */
@Composable
fun TemplateInterpretScreen(
    viewModel: TemplateInterpretViewModel,
    onBack: () -> Unit,
    onConfirmed: (templateName: String, sections: List<SectionResponse>) -> Unit,
    onFallbackToPreset: () -> Unit,
    onFallbackToEdit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = RmTheme.colors

    LaunchedEffect(state.gate) {
        if (state.gate is InterpretGateState.Confirmed) {
            val confirmed = state.gate as InterpretGateState.Confirmed
            onConfirmed(confirmed.templateName, confirmed.sections)
            viewModel.consumeConfirmed()
        }
    }

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
                title = "회사 양식 붙여넣기",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()

        when (val gate = state.gate) {
            InterpretGateState.Interpreting -> LoadingState(contentModifier, caption = "양식을 분석하는 중이에요")

            InterpretGateState.Idle -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
            ) {
                Text(
                    text = "회사가 요구하는 이력서 양식 설명·항목 목록을 아래에 붙여넣으세요.",
                    style = RmTextStyles.caption,
                    color = colors.textTertiary,
                )
                RmTextField(
                    value = state.pastedText,
                    onValueChange = viewModel::onPastedTextChange,
                    label = "양식 텍스트 *",
                    placeholder = "예: 지원서는 자기소개, 지원동기, 주요 경력 항목으로 구성해 주세요.",
                    error = state.pastedTextError,
                    singleLine = false,
                )
                PrimaryButton(text = "양식 해석하기", onClick = viewModel::interpret)
            }

            is InterpretGateState.Gate -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
            ) {
                Text(
                    text = "다음 섹션 구조로 양식을 만들어요. 이름을 정해 저장하세요.",
                    style = RmTextStyles.caption,
                    color = colors.textTertiary,
                )
                // 읽기 전용 섹션 목록 — FU-C: 섹션 편집은 로드맵.
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                    gate.sections.forEachIndexed { index, section ->
                        ReadOnlySectionRow(index = index, section = section)
                    }
                }
                RmTextField(
                    value = state.templateName,
                    onValueChange = viewModel::onTemplateNameChange,
                    label = "양식 이름 *",
                    placeholder = "예: 토스 백엔드 지원용",
                    error = state.templateNameError,
                )
                PrimaryButton(
                    text = "이대로 만들기",
                    onClick = viewModel::confirmGate,
                )
                SecondaryButton(
                    text = "처음으로",
                    onClick = viewModel::resetToIdle,
                )
            }

            InterpretGateState.Fallback -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
            ) {
                Text(
                    text = "양식을 알아보기 어려워요.",
                    style = RmTextStyles.titleL,
                    color = colors.textPrimary,
                )
                Text(
                    text = "프리셋을 고르거나, AI가 알아서 구조를 정하게 둘 수 있어요.",
                    style = RmTextStyles.bodyMr,
                    color = colors.textSecondary,
                )
                PrimaryButton(text = "프리셋 고르기", onClick = onFallbackToPreset)
                SecondaryButton(text = "직접 만들기", onClick = onFallbackToEdit)
                SecondaryButton(text = "다시 붙여넣기", onClick = viewModel::resetToIdle)
            }

            // Confirmed는 LaunchedEffect에서 처리하므로 여기선 로딩 표시.
            is InterpretGateState.Confirmed -> LoadingState(contentModifier, caption = "이동 중이에요")
        }
    }
}

/** 게이트 확정 전 읽기 전용 섹션 행. 섹션 편집은 로드맵(FU-C). */
@Composable
private fun ReadOnlySectionRow(index: Int, section: SectionResponse) {
    val colors = RmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RmRadius.card))
            .border(RmSize.hairline, colors.borderSubtle, RoundedCornerShape(RmRadius.card))
            .padding(horizontal = RmSpacing.space4, vertical = RmSpacing.space3),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. ${section.name}",
                style = RmTextStyles.label,
                color = colors.textPrimary,
            )
            Text(
                text = buildString {
                    append(section.character.label)
                    if (section.required) append(" · 필수")
                },
                style = RmTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}
