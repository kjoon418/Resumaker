package watson.resumaker.feature.experience

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.ExperienceResponse
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.BottomActionBar
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ExperienceIconChip
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTopBar
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.TypeBadge
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmMotion
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §8.3 경험 목록. 로딩/빈/에러/목록을 1급 상태로 구현한다.
 */
@Composable
fun ExperienceListScreen(
    viewModel: ExperienceListViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            // UX-5: 재시도 가능한 실패면 "다시 시도" 액션을 노출(막다른 길 방지).
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
                // CQ-2: 액션 없이 dismiss된 경우 스테일 retryableDelete 클리어.
                viewModel.clearRetryableDelete()
            }
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = true,
        topBar = { RmTopBar(title = "내 경험", onBack = onBack) },
        floatingBottom = {
            BottomActionBar {
                PrimaryButton(text = "경험 기록하기", onClick = onCreate, pressedScale = RmMotion.pressScaleStrong)
            }
        },
    ) { contentModifier ->
        when {
            state.loading -> Box(
                contentModifier.padding(
                    start = RmSpacing.contentPadding,
                    end = RmSpacing.contentPadding,
                    top = RmSpacing.space4,
                ),
            ) {
                SkeletonList(showLeadingChip = true)
            }
            state.errorMessage != null -> Box(contentModifier.padding(RmSpacing.contentPadding)) {
                ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
            }
            state.items.isEmpty() -> EmptyState(
                modifier = contentModifier,
                icon = RmIcons.Note,
                title = "아직 기록한 경험이 없어요",
                description = "첫 경험을 기록해 볼까요? 무엇을 했는지 한 줄이면 충분해요.",
                actionText = "경험 기록하기",
                onAction = onCreate,
            )
            else -> LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(
                    start = RmSpacing.contentPadding,
                    end = RmSpacing.contentPadding,
                    top = RmSpacing.space4,
                    bottom = RmSpacing.space10 + RmSpacing.space10,
                ),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
            ) {
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
            // UX-9: 카드 본문 탭(열기)과 삭제 X의 오탭을 줄이기 위해 본문과 간격을 띄우고,
            // IconButton 기본 48dp 터치 영역을 유지한다(스와이프/⋯ 도입은 핸드오프의 트레이드오프 참조).
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
