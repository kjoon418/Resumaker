package watson.resumaker.feature.target

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
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.HeaderTab
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
 * 디자인 시스템 §8.5 목표 목록(웹). 전체폭 헤더 + 반응형 카드 그리드 + 인라인 "추가"(WX-5/6).
 *
 * [pendingMessage]: 저장 후 복귀 시 1회 노출할 성공 스낵바(WX-4/16).
 */
@Composable
fun TargetListScreen(
    viewModel: TargetListViewModel,
    selectedTab: HeaderTab,
    pendingMessage: String?,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSelectTab: (HeaderTab) -> Unit,
    onOpenMyPage: () -> Unit,
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
            ) {
                PageHeaderRow(onCreate = onCreate)
                EmptyState(
                    icon = RmIcons.Target,
                    title = "아직 등록한 목표가 없어요",
                    description = "어떤 회사·직무를 겨냥하나요? 공고를 붙여넣어도 좋아요.",
                    actionText = "목표 추가하기",
                    onAction = onCreate,
                )
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
                    PageHeaderRow(onCreate = onCreate)
                }
                items(state.items, key = { it.id }) { item ->
                    TargetRow(item = item, onOpen = { onOpen(item.id) }, onDelete = { viewModel.requestDelete(item) })
                }
            }
        }
    }

    state.pendingDelete?.let { target ->
        ConfirmDialog(
            title = "이 목표를 삭제할까요?",
            description = "‘${targetTitle(target)}’ 목표가 삭제되며 되돌릴 수 없어요.",
            confirmText = "삭제",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** 페이지 제목 + 우측 인라인 "추가" 버튼(WX-6). */
@Composable
private fun PageHeaderRow(onCreate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = RmSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "내 목표",
            style = RmTextStyles.titleL,
            color = RmTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        InlineAddButton(text = "목표 추가하기", onClick = onCreate)
    }
}

@Composable
private fun TargetRow(
    item: TargetResponse,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItemCard(
        title = targetTitle(item),
        meta = item.recruitDirection,
        onClick = onOpen,
        badge = { StrategyStatusBadge(status = item.strategyStatus) },
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

/** 회사명·직무명을 합친 제목(둘 다 없으면 채용방향 앞부분). */
internal fun targetTitle(item: TargetResponse): String {
    val company = item.companyName?.takeIf { it.isNotBlank() }
    val job = item.jobTitle?.takeIf { it.isNotBlank() }
    return when {
        company != null && job != null -> "$company · $job"
        company != null -> company
        job != null -> job
        else -> "겨냥하는 목표"
    }
}
