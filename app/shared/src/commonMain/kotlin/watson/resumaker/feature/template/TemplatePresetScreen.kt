package watson.resumaker.feature.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.TemplatePresetResponse
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 프리셋 양식 선택 화면(FU-B). 서비스가 제공하는 표준 이력서 양식 목록을 보여준다.
 * 하나를 고르면 이름+섹션을 편집 화면에 미리 채워 사용자가 자기만의 양식으로 다듬게 한다.
 *
 * IA 검토 필요: 프리셋 선택 화면의 정보구조(목록 vs 카드 그리드, 섹션 미리보기 깊이)는
 * 최종 디자이너 리뷰 대상이다.
 */
@Composable
fun TemplatePresetScreen(
    viewModel: TemplatePresetViewModel,
    onBack: () -> Unit,
    onPresetSelected: (TemplatePresetResponse) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.selectedPreset) {
        state.selectedPreset?.let {
            onPresetSelected(it)
            viewModel.consumeSelectedPreset()
        }
    }

    AppScaffold(
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = "프리셋 선택",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> LoadingState(contentModifier, caption = "불러오는 중이에요")
            state.errorMessage != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(
                    message = state.errorMessage!!,
                    onRetry = viewModel::load,
                    title = "불러오지 못했어요",
                )
            }
            else -> Column(contentModifier.padding(horizontal = pad)) {
                Text(
                    text = "표준 양식 중 하나를 골라 편집 화면에서 다듬어 저장하세요.",
                    style = RmTextStyles.caption,
                    color = RmTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = RmSpacing.space4, bottom = RmSpacing.space3),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
                    contentPadding = PaddingValues(bottom = RmSpacing.space10),
                ) {
                    items(state.presets, key = { it.key }) { preset ->
                        ListItemCard(
                            title = preset.name,
                            meta = presetSectionSummary(preset),
                            onClick = { viewModel.selectPreset(preset) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 프리셋 섹션 구성을 한 줄로 요약한다(최대 3개 이름). */
private fun presetSectionSummary(preset: TemplatePresetResponse): String {
    val count = preset.sections.size
    val names = preset.sections.take(3).joinToString(" · ") { it.name }
    return if (names.isBlank()) "섹션 ${count}개" else "섹션 ${count}개 · $names"
}
