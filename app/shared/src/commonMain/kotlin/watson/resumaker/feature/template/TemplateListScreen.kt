package watson.resumaker.feature.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.TemplateResponse
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.component.GhostButton
import watson.resumaker.ui.component.InlineAddButton
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.LocalContentMaxWidth
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.gridColumnsFor
import watson.resumaker.ui.theme.pagePadding

/**
 * 이력서 양식 목록(웹, FU-A). 전체폭 헤더 + 반응형 카드 그리드 + 인라인 "추가". TargetListScreen을 미러한다.
 *
 * [pendingMessage]: 저장 후 복귀 시 1회 노출할 성공 스낵바(WX-4/16).
 */
@Composable
fun TemplateListScreen(
    viewModel: TemplateListViewModel,
    selectedTab: HeaderTab,
    pendingMessage: String?,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSelectTab: (HeaderTab) -> Unit,
    onOpenMyPage: () -> Unit,
    onStartFromPreset: () -> Unit = {},
    onStartFromPaste: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            val canRetry = state.retryableDelete != null
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (canRetry) "다시 시도" else null,
                duration = SnackbarDuration.Short,
            )
            viewModel.consumeSnackbar()
            if (canRetry && result == SnackbarResult.ActionPerformed) {
                viewModel.retryDelete()
            } else if (canRetry) {
                viewModel.clearRetryableDelete()
            }
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.WIDE,
        header = { windowSize ->
            AppHeader(
                selected = selectedTab,
                onSelectTab = onSelectTab,
                onOpenAccount = onOpenMyPage,
                windowSize = windowSize,
                horizontalPadding = windowSize.pagePadding(),
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        val columns = gridColumnsFor(windowSize, LocalContentMaxWidth.current)
        when {
            state.loading -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                SkeletonList(showLeadingChip = false)
            }
            state.errorMessage != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
            }
            state.items.isEmpty() -> Column(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // M-2: 시작 CTA 3개 동등 노출로 인한 인지부하 해소.
                // primary는 EmptyState의 "양식 만들기" 하나로 단일화하고,
                // 프리셋·붙여넣기는 본문 아래 보조 위계(GhostButton)로 낮춘다.
                EmptyState(
                    icon = RmIcons.Note,
                    title = "아직 만든 양식이 없어요",
                    description = "회사가 요구하는 섹션 구조를 한 번 만들어 두면, 지원처를 바꿔가며 다시 쓸 수 있어요.",
                    actionText = "양식 만들기",
                    onAction = onCreate,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
                    modifier = Modifier.padding(top = RmSpacing.space3),
                ) {
                    GhostButton(text = "프리셋에서 시작", onClick = onStartFromPreset, fillWidth = false)
                    GhostButton(text = "회사 양식 붙여넣기", onClick = onStartFromPaste, fillWidth = false)
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = contentModifier,
                contentPadding = PaddingValues(
                    start = pad,
                    end = pad,
                    top = RmSpacing.space6,
                    bottom = RmSpacing.space10,
                ),
                horizontalArrangement = Arrangement.spacedBy(RmSpacing.space3),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PageHeaderRow(
                        onCreate = onCreate,
                        onStartFromPreset = onStartFromPreset,
                        onStartFromPaste = onStartFromPaste,
                    )
                }
                items(state.items, key = { it.id }) { item ->
                    TemplateRow(item = item, onOpen = { onOpen(item.id) }, onDelete = { viewModel.requestDelete(item) })
                }
            }
        }
    }

    state.pendingDelete?.let { template ->
        ConfirmDialog(
            title = "이 양식을 삭제할까요?",
            description = "‘${template.name}’ 양식이 삭제되며 되돌릴 수 없어요.",
            confirmText = "삭제",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** 페이지 제목 + 우측 인라인 추가·프리셋·붙여넣기 진입점(WX-6, FU-B/C). */
@Composable
private fun PageHeaderRow(
    onCreate: () -> Unit,
    onStartFromPreset: () -> Unit,
    onStartFromPaste: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = RmSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "내 양식",
                style = RmTextStyles.titleL,
                color = RmTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            InlineAddButton(text = "양식 만들기", onClick = onCreate)
        }
        // FU-B/C 진입점: 프리셋에서 시작 · 회사 양식 붙여넣기.
        // IA 검토 필요: 진입점 배치·버튼 레이블은 최종 디자이너 리뷰 대상이다.
        Row(
            horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
        ) {
            GhostButton(text = "프리셋에서 시작", onClick = onStartFromPreset, fillWidth = false)
            GhostButton(text = "회사 양식 붙여넣기", onClick = onStartFromPaste, fillWidth = false)
        }
    }
}

@Composable
private fun TemplateRow(
    item: TemplateResponse,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItemCard(
        title = item.name,
        meta = templateSummary(item),
        onClick = onOpen,
        trailing = {
            IconButton(onClick = onDelete, modifier = Modifier.padding(start = RmSpacing.space2)) {
                Icon(
                    imageVector = RmIcons.Close,
                    contentDescription = "삭제",
                    tint = RmTheme.colors.textTertiary,
                    modifier = Modifier.size(RmSize.iconSm),
                )
            }
        },
    )
}

/** 섹션 개수와 앞쪽 섹션 이름을 요약한 메타 문구. */
internal fun templateSummary(item: TemplateResponse): String {
    val count = item.sections.size
    val names = item.sections.take(3).joinToString(" · ") { it.name }
    return if (names.isBlank()) "섹션 ${count}개" else "섹션 ${count}개 · $names"
}
