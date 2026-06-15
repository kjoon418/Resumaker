package watson.resumaker.feature.experience

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
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
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.ui.component.AppHeader
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ExperienceIconChip
import watson.resumaker.ui.component.HeaderTab
import watson.resumaker.ui.component.InlineAddButton
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.LocalContentMaxWidth
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.TypeBadge
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.gridColumnsFor
import watson.resumaker.ui.theme.pagePadding

/**
 * 디자인 시스템 §8.3 경험 목록(웹). 전체폭 헤더 + 1120dp 콘텐츠 + 반응형 카드 그리드(WX-5).
 * 페이지 헤더에 인라인 "추가" 버튼(WX-6). 로딩/빈/에러/목록을 1급 상태로 구현한다.
 *
 * [pendingMessage]: 저장 후 복귀 시 1회 노출할 성공 스낵바(WX-4/16).
 */
@Composable
fun ExperienceListScreen(
    viewModel: ExperienceListViewModel,
    selectedTab: HeaderTab,
    pendingMessage: String?,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onSelectTab: (HeaderTab) -> Unit,
    onOpenMyPage: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // WX-4/16: 저장 후 복귀 성공 스낵바 1회.
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
                SkeletonList(showLeadingChip = true)
            }
            state.errorMessage != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
            }
            state.items.isEmpty() -> androidx.compose.foundation.layout.Column(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                PageHeaderRow(onCreate = onCreate)
                EmptyState(
                    icon = RmIcons.Note,
                    title = "아직 기록한 경험이 없어요",
                    description = "첫 경험을 기록해 볼까요? 무엇을 했는지 한 줄이면 충분해요.",
                    actionText = "경험 기록하기",
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
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    PageHeaderRow(onCreate = onCreate)
                }
                items(state.items, key = { it.id }) { item ->
                    ExperienceRow(item = item, onOpen = { onOpen(item.id) }, onDelete = { viewModel.requestDelete(item) })
                }
            }
        }
    }

    state.pendingDelete?.let { target ->
        ConfirmDialog(
            title = "이 경험을 삭제할까요?",
            description = "‘${target.title}’ 기록이 삭제되며 되돌릴 수 없어요.",
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
            text = "내 경험",
            style = RmTextStyles.titleL,
            color = RmTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        InlineAddButton(text = "경험 기록하기", onClick = onCreate)
    }
}

@Composable
private fun ExperienceRow(
    item: ExperienceResponse,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val period = formatPeriod(item.periodStart, item.periodEnd)
    ListItemCard(
        title = item.title,
        meta = period,
        leading = { ExperienceIconChip(item.type) },
        badge = { TypeBadge(item.type) },
        onClick = onOpen,
        trailing = {
            // 본문 탭(열기)과 삭제 X 오탭 완화 — 본문과 간격 확보 + 48dp 터치 영역 유지.
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

/** periodStart~periodEnd 표시(둘 다 없으면 null). */
internal fun formatPeriod(start: String?, end: String?): String? = when {
    start != null && end != null -> "$start ~ $end"
    start != null -> "$start ~"
    end != null -> "~ $end"
    else -> null
}
