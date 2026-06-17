package watson.resumaker.feature.artifact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.platform.copyToClipboard
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.Badge
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.GhostButton
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.StatusBadge
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 산출물 열람 화면(수용 기준 12). 활성 버전의 항목별 내용·상태 배지·출처를 표시한다.
 * 부분 실패 항목은 상태를 명확히 고지하고(가짜 성공 금지 — 도메인 §306), 항목/전체 복사를 제공한다(§6, 복사는 클라 책임).
 */
@Composable
fun ArtifactScreen(
    viewModel: ArtifactViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var copyMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copyMessage) {
        copyMessage?.let {
            snackbarHostState.showSnackbar(it)
            copyMessage = null
        }
    }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = if (state.kind == ArtifactKind.PORTFOLIO) "포트폴리오" else "이력서",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, windowSize ->
        val pad = windowSize.pagePadding()
        when {
            state.loading -> Box(
                contentModifier.padding(horizontal = pad).padding(top = RmSpacing.space6),
            ) {
                SkeletonList(showLeadingChip = false)
            }
            state.errorMessage != null -> Box(contentModifier.padding(pad)) {
                ErrorBanner(message = state.errorMessage!!, onRetry = viewModel::load)
            }
            else -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = pad)
                    .padding(top = RmSpacing.space6, bottom = RmSpacing.space10),
                verticalArrangement = Arrangement.spacedBy(RmSpacing.space4),
            ) {
                if (state.hasFailedSections) {
                    InfoCard(icon = RmIcons.Warning, title = "일부 항목을 만들지 못했어요") {
                        Text(
                            text = "실패한 항목은 아래에 표시돼 있어요. 해당 항목은 잠시 후 다시 시도할 수 있어요.",
                            style = RmTextStyles.bodyS,
                            color = RmTheme.colors.onPrimaryContainer,
                        )
                    }
                }

                if (state.hasCopyableContent) {
                    GhostButton(
                        text = "전체 복사",
                        onClick = {
                            copyToClipboard(state.fullCopyText)
                            copyMessage = "전체 내용을 복사했어요."
                        },
                    )
                }

                state.sections.forEach { section ->
                    SectionCard(
                        section = section,
                        onCopy = {
                            copyToClipboard(section.content)
                            copyMessage = "항목을 복사했어요."
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: ArtifactSectionUi,
    onCopy: () -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = section.definitionKey,
                    style = RmTextStyles.label,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SectionStatusBadge(section)
            }

            if (section.failed) {
                // 실패 항목은 가짜 내용을 보여주지 않는다(신뢰성 가드레일). 실패 사유만 고지한다.
                Text(
                    text = failedMessage(section),
                    style = RmTextStyles.bodyS,
                    color = colors.danger,
                )
            } else {
                Text(
                    text = section.content,
                    style = RmTextStyles.bodyS,
                    color = colors.textBody,
                )
                if (section.sourceExperienceIds.isNotEmpty()) {
                    Text(
                        text = "출처 경험 ${section.sourceExperienceIds.size}개",
                        style = RmTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
                Box(modifier = Modifier.size(RmSpacing.space1))
                CopyRow(onCopy = onCopy)
            }
        }
    }
}

@Composable
private fun CopyRow(onCopy: () -> Unit) {
    val colors = RmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = RmIcons.Copy,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(RmSize.iconSm),
        )
        Text(
            text = "복사",
            style = RmTextStyles.caption,
            color = colors.primary,
            modifier = Modifier.padding(start = RmSpacing.space1),
        )
    }
}

@Composable
private fun SectionStatusBadge(section: ArtifactSectionUi) {
    val colors = RmTheme.colors
    when (section.status) {
        watson.resumaker.model.type.SectionStatus.GENERATED ->
            Badge(text = "완료", fg = colors.success, bg = colors.successBg)
        watson.resumaker.model.type.SectionStatus.GENERATION_FAILED ->
            Badge(text = "생성 실패", fg = colors.danger, bg = colors.dangerBg)
        watson.resumaker.model.type.SectionStatus.VALIDATION_FAILED ->
            Badge(text = "검증 실패", fg = colors.danger, bg = colors.dangerBg)
        watson.resumaker.model.type.SectionStatus.GENERATING ->
            StatusBadge(text = "생성 중")
    }
}

private fun failedMessage(section: ArtifactSectionUi): String = when (section.status) {
    watson.resumaker.model.type.SectionStatus.GENERATION_FAILED -> "이 항목을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
    watson.resumaker.model.type.SectionStatus.VALIDATION_FAILED -> "이 항목이 검증을 통과하지 못했어요. 다시 시도해 주세요."
    else -> "이 항목을 표시할 수 없어요."
}
