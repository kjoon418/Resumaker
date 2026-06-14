package watson.resumaker.feature.target

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.dto.TargetResponse
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.BottomActionBar
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.EmptyState
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.ListItemCard
import watson.resumaker.ui.component.LoadingState
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmTopBar
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmMotion
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §8.5 목표 목록.
 */
@Composable
fun TargetListScreen(
    viewModel: TargetListViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        columnBackground = true,
        topBar = { RmTopBar(title = "내 목표", onBack = onBack) },
        floatingBottom = {
            BottomActionBar {
                PrimaryButton(text = "목표 추가하기", onClick = onCreate, pressedScale = RmMotion.pressScaleStrong)
            }
        },
    ) { contentModifier ->
        when {
            state.loading -> LoadingState(contentModifier, caption = "목표를 불러오는 중이에요")
            state.errorMessage != null -> Box(contentModifier.padding(RmSpacing.contentPadding)) {
                ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
            }
            state.items.isEmpty() -> EmptyState(
                modifier = contentModifier,
                icon = RmIcons.Target,
                title = "아직 등록한 목표가 없어요",
                description = "어떤 회사·직무를 겨냥하나요? 공고를 붙여넣어도 좋아요.",
                actionText = "목표 추가하기",
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
        trailing = {
            IconButton(onClick = onDelete) {
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
