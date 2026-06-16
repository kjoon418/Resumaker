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
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.theme.RmIcons
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
    onStartFromEdit: () -> Unit = {},
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
            // M-5: 카드 리스트 로딩은 스피너 대신 스켈레톤으로 레이아웃 점프를 줄인다(프리셋은 칩 없는 카드).
            state.loading -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                SkeletonList(showLeadingChip = false)
            }
            state.errorMessage != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(
                    message = state.errorMessage!!,
                    onRetry = viewModel::load,
                    title = "불러오지 못했어요",
                )
            }
            // M-4: 로딩X·에러X인데 프리셋이 비면 막다른 길이 되지 않도록 EmptyState로 대안을 제시한다.
            state.presets.isEmpty() -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                EmptyState(
                    icon = RmIcons.Inbox,
                    title = "제공 중인 프리셋이 없어요",
                    description = "직접 만들거나 회사 양식을 붙여넣어 시작할 수 있어요.",
                    actionText = "직접 만들기",
                    onAction = onStartFromEdit,
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

/**
 * 프리셋 섹션 구성을 한 줄로 요약한다(최대 3개 이름).
 * L-2: 섹션 수가 3개를 넘으면 "+N개 더"를 덧붙여 프리셋 간 차이를 스캔하기 쉽게 한다.
 */
internal fun presetSectionSummary(preset: TemplatePresetResponse): String {
    val count = preset.sections.size
    val names = preset.sections.take(3).joinToString(" · ") { it.name }
    if (names.isBlank()) return "섹션 ${count}개"
    val remainder = count - 3
    val suffix = if (remainder > 0) " +${remainder}개 더" else ""
    return "섹션 ${count}개 · $names$suffix"
}
