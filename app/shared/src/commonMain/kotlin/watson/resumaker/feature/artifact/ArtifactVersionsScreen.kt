package watson.resumaker.feature.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.platform.copyToClipboard
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.Badge
import watson.resumaker.ui.component.ConfirmDialog
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.GhostButton
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.StatusBadge
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.pagePadding

/**
 * 산출물 버전 기록·비교·복원 화면(§271~290·§344~374). 모든 버전을 생성순으로 보여주고(활성 표시·생성시각), 두
 * 버전을 골라 definitionKey로 같은 항목을 맞춰 나란히 비교하며(§363), 한 버전을 골라 복원(활성 전환 — §287)한다.
 *
 * 비교 UI(MVP): 좌·우 두 버전을 칩으로 선택하면 항목별로 좌/우 내용을 나란히 대비한다. 한쪽에만 있는 항목(키 불일치)은
 * "이 버전엔 없음"으로 정직하게 고지한다(가짜 성공 금지). 복원은 활성이 바뀌므로 ConfirmDialog로 확인한다(막다른 길 금지).
 */
@Composable
fun ArtifactVersionsScreen(
    viewModel: ArtifactVersionsViewModel,
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

    // 복원 성공·실패 안내를 스낵바로 한 번만 띄운다.
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    // 복원 확인 대상 버전(null이면 다이얼로그 닫힘). 활성이 바뀌므로 확인을 거친다(§UX).
    var confirmRestoreId by remember { mutableStateOf<String?>(null) }

    AppScaffold(
        snackbarHostState = snackbarHostState,
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = if (state.kind == ArtifactKind.PORTFOLIO) "포트폴리오 버전 기록" else "이력서 버전 기록",
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
                // 버전 선택기: 좌(기준)·우(대상)를 각각 고른다. 최신이 위로 오게 뒤집어 보여준다.
                val ordered = state.versions.asReversed()

                VersionPickerSection(
                    label = "기준 버전",
                    versions = ordered,
                    selectedId = state.leftVersionId,
                    onSelect = viewModel::selectLeftVersion,
                )

                if (state.canCompare) {
                    VersionPickerSection(
                        label = "비교할 버전",
                        versions = ordered,
                        selectedId = state.rightVersionId,
                        onSelect = viewModel::selectRightVersion,
                    )
                }

                // 선택된 기준 버전 메타 + 복원 버튼(활성이면 비활성).
                state.leftVersion?.let { left ->
                    VersionMetaRow(
                        version = left,
                        restoreInFlight = state.restoreInFlight,
                        restoringThis = state.restoringVersionId == left.versionId,
                        onRestore = { confirmRestoreId = left.versionId },
                    )
                }

                if (state.canCompare) {
                    DiffList(
                        rows = state.diffRows,
                        leftLabel = state.leftVersion?.label ?: "기준",
                        rightLabel = state.rightVersion?.label ?: "비교",
                        onCopy = { content ->
                            copyToClipboard(content)
                            copyMessage = "항목을 복사했어요."
                        },
                    )
                } else {
                    // 버전이 하나뿐이면 비교 없이 그 버전의 항목만 보여준다.
                    SingleVersionList(
                        version = state.leftVersion,
                        onCopy = { content ->
                            copyToClipboard(content)
                            copyMessage = "항목을 복사했어요."
                        },
                    )
                }
            }
        }
    }

    confirmRestoreId?.let { versionId ->
        val label = state.versions.firstOrNull { it.versionId == versionId }?.label ?: "이 버전"
        ConfirmDialog(
            title = "$label(으)로 되돌릴까요?",
            description = "이 버전을 현재 활성 버전으로 바꿔요. 다른 버전은 그대로 남아 있어 언제든 다시 되돌릴 수 있어요.",
            confirmText = "되돌리기",
            destructive = false,
            onConfirm = {
                viewModel.restoreVersion(versionId)
                confirmRestoreId = null
            },
            onDismiss = { confirmRestoreId = null },
        )
    }
}

/**
 * 버전 선택기 한 줄: 라벨 + 버전 칩 목록. 선택된 칩은 primary 톤, 활성 버전은 별도 배지로 표시한다.
 * 칩이 가로로 넘치면 자연스럽게 다음 줄로 흐르도록 단순 Column/Row 조합 대신 세로 목록으로 둔다(정보밀도·반응형).
 */
@Composable
private fun VersionPickerSection(
    label: String,
    versions: List<VersionUi>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val colors = RmTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
        Text(text = label, style = RmTextStyles.label, color = colors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space1)) {
            versions.forEach { v ->
                VersionChipRow(
                    version = v,
                    selected = v.versionId == selectedId,
                    onClick = { onSelect(v.versionId) },
                )
            }
        }
    }
}

@Composable
private fun VersionChipRow(
    version: VersionUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) colors.primaryContainer else colors.surfaceSubtle,
                RoundedCornerShape(RmRadius.chip),
            )
            .padding(horizontal = RmSpacing.space3, vertical = RmSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
    ) {
        Text(
            text = version.label,
            style = RmTextStyles.bodyS.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) colors.onPrimaryContainer else colors.textBody,
            modifier = Modifier.weight(1f),
        )
        if (version.active) {
            Badge(text = "활성", fg = colors.success, bg = colors.successBg)
        }
        Text(
            text = formatCreatedAt(version.createdAt),
            style = RmTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

/** 기준 버전 메타 + 복원 버튼. 활성 버전은 복원 의미가 없어 버튼을 숨긴다. */
@Composable
private fun VersionMetaRow(
    version: VersionUi,
    restoreInFlight: Boolean,
    restoringThis: Boolean,
    onRestore: () -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
                ) {
                    Text(text = version.label, style = RmTextStyles.bodyM, color = colors.textPrimary)
                    if (version.active) {
                        Badge(text = "활성", fg = colors.success, bg = colors.successBg)
                    }
                }
                Text(
                    text = "만든 시각 ${formatCreatedAt(version.createdAt)}",
                    style = RmTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = RmSpacing.space1),
                )
            }
            when {
                version.active -> StatusBadge(
                    text = "현재 버전",
                    fg = colors.textTertiary,
                    bg = colors.surfaceSubtle,
                )
                restoringThis -> StatusBadge(text = "되돌리는 중")
                else -> GhostButton(
                    text = "이 버전으로 되돌리기",
                    onClick = onRestore,
                    enabled = !restoreInFlight,
                    fillWidth = false,
                )
            }
        }
    }
}

/** definitionKey로 맞춘 비교 행 목록. 각 행은 같은 항목을 좌/우 버전에서 대비한다(§363). */
@Composable
private fun DiffList(
    rows: List<VersionDiffRow>,
    leftLabel: String,
    rightLabel: String,
    onCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space4)) {
        rows.forEach { row ->
            DiffCard(row = row, leftLabel = leftLabel, rightLabel = rightLabel, onCopy = onCopy)
        }
    }
}

@Composable
private fun DiffCard(
    row: VersionDiffRow,
    leftLabel: String,
    rightLabel: String,
    onCopy: (String) -> Unit,
) {
    val colors = RmTheme.colors
    RmCard {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.definitionKey,
                    style = RmTextStyles.label,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (row.changed) {
                    Badge(text = "다름", fg = colors.warning, bg = colors.warningBg)
                }
            }
            // 좁은 폭(NARROW)이라 좌→우를 세로로 쌓아 대비한다(반응형: 가로 분할은 폭 부족 시 가독성 저하).
            DiffSide(label = leftLabel, section = row.left, onCopy = onCopy)
            DiffSide(label = rightLabel, section = row.right, onCopy = onCopy)
        }
    }
}

@Composable
private fun DiffSide(
    label: String,
    section: ArtifactSectionUi?,
    onCopy: (String) -> Unit,
) {
    val colors = RmTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSubtle, RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space1),
    ) {
        Text(text = label, style = RmTextStyles.caption, color = colors.textTertiary)
        when {
            section == null -> Text(
                text = "이 버전엔 없는 항목이에요.",
                style = RmTextStyles.bodyS,
                color = colors.textTertiary,
            )
            section.failed -> Text(
                text = "이 항목을 만들지 못했어요.",
                style = RmTextStyles.bodyS,
                color = colors.danger,
            )
            else -> {
                Text(text = section.content, style = RmTextStyles.bodyS, color = colors.textBody)
                if (section.content.isNotBlank()) {
                    Text(
                        text = "복사",
                        style = RmTextStyles.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary,
                        modifier = Modifier.clickable { onCopy(section.content) },
                    )
                }
            }
        }
    }
}

/** 버전이 하나뿐일 때 비교 없이 그 버전의 항목만 보여준다. */
@Composable
private fun SingleVersionList(
    version: VersionUi?,
    onCopy: (String) -> Unit,
) {
    val colors = RmTheme.colors
    version ?: return
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space4)) {
        version.sections.forEach { section ->
            RmCard {
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                    Text(text = section.definitionKey, style = RmTextStyles.label, color = colors.textPrimary)
                    if (section.failed) {
                        Text(
                            text = "이 항목을 만들지 못했어요.",
                            style = RmTextStyles.bodyS,
                            color = colors.danger,
                        )
                    } else {
                        Text(text = section.content, style = RmTextStyles.bodyS, color = colors.textBody)
                        if (section.content.isNotBlank()) {
                            Text(
                                text = "복사",
                                style = RmTextStyles.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary,
                                modifier = Modifier.clickable { onCopy(section.content) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 생성시각(ISO 문자열)을 날짜·시각만 간단히 다듬는다. 파싱 실패 시 원문 그대로(방어적). */
private fun formatCreatedAt(iso: String): String {
    // "2024-01-02T03:04:05..." → "2024-01-02 03:04". 시간대·소수초는 표시에서 생략한다.
    val t = iso.indexOf('T')
    if (t < 0) return iso
    val date = iso.substring(0, t)
    val rest = iso.substring(t + 1)
    val time = rest.take(5) // HH:MM
    return if (time.length == 5) "$date $time" else date
}
