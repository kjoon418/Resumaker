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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.resumaker.model.type.ArtifactKind
import watson.resumaker.model.type.SectionStatus
import watson.resumaker.platform.copyToClipboard
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.Badge
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.ErrorBanner
import watson.resumaker.ui.component.GhostButton
import watson.resumaker.ui.component.InfoCard
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.component.PrimaryButton
import watson.resumaker.ui.component.RmCard
import watson.resumaker.ui.component.RmTextField
import watson.resumaker.ui.component.SkeletonList
import watson.resumaker.ui.component.StatusBadge
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
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
    onViewVersions: () -> Unit = {},
    /** 품질 점검 진입(RESUME 전용, QC10). 화면이 RESUME 종류일 때만 버튼을 노출한다. */
    onStartQualityReview: () -> Unit = {},
    /** 비차단 품질 개선 진행 카드의 "확인하기" — 준비된 개선안(후보) 비교·채택 화면으로 이동(§3). */
    onConfirmQualityImprovement: (jobId: String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 비차단 품질 개선 진행 카드 복원(§3): 화면 (재)진입 시 최신 개선 작업을 조회해 카드로 띄운다.
    LaunchedEffect(Unit) { viewModel.refreshQualityJob() }

    var copyMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copyMessage) {
        copyMessage?.let {
            snackbarHostState.showSnackbar(it)
            copyMessage = null
        }
    }

    // 항목 액션(재생성/편집) 결과 안내: 동시 재생성 409·정리 고지·실패 안내를 스낵바로 한 번만 띄운다.
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    // 편집기를 여는 항목(화면 단일 상태). 재생성 한도 초과(EDIT_MANUALLY) 시 VM이 보내는 일회성 신호로 자동 연다.
    var editingSectionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.editPromptSectionId) {
        state.editPromptSectionId?.let {
            editingSectionId = it
            viewModel.consumeEditPrompt()
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

                // 비차단 품질 개선 진행 카드(§3): 다듬는 중/준비됨/실패를 생성 비동기처럼 알린다(빈 화면 대기 해소).
                state.qualityImprovement?.let { card ->
                    QualityImprovementCard(
                        card = card,
                        onConfirm = { onConfirmQualityImprovement(card.jobId) },
                        onDismiss = viewModel::dismissQualityJob,
                    )
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

                // 버전 기록·비교 진입(§271~290·§363). 복귀 시 VM이 재생성돼 load()가 서버 최신(복원 결과 포함)을
                // 가져오므로 별도 갱신 신호 없이도 갱신된 활성 버전이 자동 반영된다(load-after-initial — §287).
                GhostButton(
                    text = "버전 기록·비교 보기",
                    onClick = onViewVersions,
                )

                // 품질 점검 진입(QC10): RESUME 전용. 포트폴리오는 진입점을 노출하지 않는다.
                if (state.kind == ArtifactKind.RESUME) {
                    GhostButton(
                        text = "품질 점검",
                        onClick = onStartQualityReview,
                    )
                }

                state.sections.forEach { section ->
                    SectionCard(
                        section = section,
                        inFlight = state.isSectionInFlight(section.id),
                        editing = editingSectionId == section.id,
                        onStartEdit = { editingSectionId = section.id },
                        onStopEdit = { if (editingSectionId == section.id) editingSectionId = null },
                        onCopy = {
                            copyToClipboard(section.content)
                            copyMessage = "항목을 복사했어요."
                        },
                        onRegenerate = { directive -> viewModel.regenerateSection(section.id, directive) },
                        onEdit = { content -> viewModel.editSection(section.id, content) },
                    )
                }
            }
        }
    }
}

/**
 * 비차단 품질 개선 진행 카드(§3). 산출물 생성 비동기와 동형으로, '이대로 다듬기' 후 빈 화면 대기 없이 이 카드로
 * 진행/완료/실패를 알린다. 서버 작업이 증분 진행을 추적하지 않으므로 진행은 불확정("품질 개선 중…")으로 정직하게
 * 표시한다(가짜 % 금지). 준비되면 "확인하기"로 후보 비교·채택 화면으로 이동한다.
 */
@Composable
private fun QualityImprovementCard(
    card: QualityImprovementCardUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RmTheme.colors
    when (card.phase) {
        QualityImprovementCardUi.Phase.IMPROVING ->
            InfoCard(icon = RmIcons.Sparkles, title = "품질 개선 중…") {
                Text(
                    text = "AI가 선택한 항목을 다듬고 있어요. 다 되면 여기서 알려드릴게요.",
                    style = RmTextStyles.bodyS,
                    color = colors.onPrimaryContainer,
                )
            }

        QualityImprovementCardUi.Phase.READY ->
            RmCard {
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                    Text(
                        text = if (card.candidateCount > 0) "개선안 ${card.candidateCount}개가 준비됐어요" else "개선 결과가 준비됐어요",
                        style = RmTextStyles.label,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "확인하고 원하는 항목만 골라 반영할 수 있어요.",
                        style = RmTextStyles.bodyS,
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
                    ) {
                        GhostButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
                        PrimaryButton(text = "확인하기", onClick = onConfirm, modifier = Modifier.weight(1f))
                    }
                }
            }

        QualityImprovementCardUi.Phase.FAILED ->
            InfoCard(icon = RmIcons.Warning, title = "품질 개선을 완료하지 못했어요") {
                Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
                    Text(
                        text = card.errorMessage ?: "다시 시도해 주세요.",
                        style = RmTextStyles.bodyS,
                        color = colors.onPrimaryContainer,
                    )
                    GhostButton(text = "닫기", onClick = onDismiss, fillWidth = false)
                }
            }
    }
}

@Composable
private fun SectionCard(
    section: ArtifactSectionUi,
    inFlight: Boolean,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onStopEdit: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: (directive: String?) -> Unit,
    onEdit: (content: String) -> Unit,
) {
    val colors = RmTheme.colors
    // 편집 텍스트: 현재 content를 프리필. section.content도 키에 포함해 응답 갱신 후 새 내용을 반영한다.
    var editText by remember(section.id, section.content) { mutableStateOf(section.content) }
    // 편집기가 열릴 때(버튼·자동 유도 모두) 현재 content로 다시 시드해 직전 미저장 입력 잔재를 없앤다.
    LaunchedEffect(editing) { if (editing) editText = section.content }
    var showRegenerateDialog by remember(section.id) { mutableStateOf(false) }

    RmCard {
        Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = section.definitionKey,
                    style = RmTextStyles.label,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (inFlight) {
                    // 재생성(장시간)·편집 진행 중 표시. 상태배지 대신 진행 표시로 정직하게 알린다.
                    StatusBadge(text = "처리 중")
                } else {
                    SectionStatusBadge(section)
                }
            }

            when {
                editing -> SectionEditor(
                    value = editText,
                    onValueChange = { editText = it },
                    inFlight = inFlight,
                    onSave = {
                        onEdit(editText)
                        onStopEdit()
                    },
                    onCancel = onStopEdit,
                )

                section.failed -> {
                    // 실패 항목은 가짜 내용을 보여주지 않는다(신뢰성 가드레일). 실패 사유만 고지한다.
                    Text(
                        text = failedMessage(section),
                        style = RmTextStyles.bodyS,
                        color = colors.danger,
                    )
                    // 실패해도 막다른 길이 아니다: 재생성·직접 편집으로 회복 경로를 연다(§UX).
                    SectionActionRow(
                        inFlight = inFlight,
                        showCopy = false,
                        onCopy = onCopy,
                        onRegenerate = { showRegenerateDialog = true },
                        onEdit = onStartEdit,
                    )
                }

                else -> {
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
                    SectionActionRow(
                        inFlight = inFlight,
                        showCopy = section.content.isNotBlank(),
                        onCopy = onCopy,
                        onRegenerate = { showRegenerateDialog = true },
                        onEdit = onStartEdit,
                    )
                }
            }
        }
    }

    if (showRegenerateDialog) {
        RegenerateDialog(
            onConfirm = { directive ->
                onRegenerate(directive)
                showRegenerateDialog = false
            },
            onDismiss = { showRegenerateDialog = false },
        )
    }
}

/**
 * 인라인 항목 편집기. 현재 내용을 프리필한 multiline 입력 + 저장/취소. 빈 내용은 서버가 400으로 거절하므로
 * 저장을 비활성화한다(인라인 검증 — UX 에러 가이드). 진행 중이면 입력·버튼이 잠긴다.
 */
@Composable
private fun SectionEditor(
    value: String,
    onValueChange: (String) -> Unit,
    inFlight: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val blank = value.isBlank()
    Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space2)) {
        RmTextField(
            value = value,
            onValueChange = onValueChange,
            label = "항목 내용 직접 수정",
            singleLine = false,
            minHeight = RmSize.multilineMinHeight,
            enabled = !inFlight,
            error = if (blank) "수정할 내용을 입력해 주세요." else null,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RmSpacing.space2),
        ) {
            GhostButton(
                text = "취소",
                onClick = onCancel,
                enabled = !inFlight,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "저장",
                onClick = onSave,
                enabled = !blank,
                loading = inFlight,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 항목 액션 행: 복사·다시 만들기(재생성)·직접 수정(편집). 진행 중이면 재생성/편집을 잠가 중복 액션을 막는다
 * (in-flight 가드). 실패 항목은 복사 대상이 아니므로 [showCopy]=false로 복사를 숨긴다(가짜 성공 금지).
 */
@Composable
private fun SectionActionRow(
    inFlight: Boolean,
    showCopy: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space4, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCopy) {
            SectionAction(icon = RmIcons.Copy, label = "복사", enabled = true, onClick = onCopy)
        }
        // 전용 편집/재생성 아이콘이 없어 의미가 가까운 기존 아이콘을 재사용한다(Note=직접 작성, Sparkles=AI 재생성).
        SectionAction(icon = RmIcons.Note, label = "수정", enabled = !inFlight, onClick = onEdit)
        SectionAction(icon = RmIcons.Sparkles, label = "다시 만들기", enabled = !inFlight, onClick = onRegenerate)
    }
}

@Composable
private fun SectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = RmTheme.colors
    val tint = if (enabled) colors.primary else colors.textTertiary
    Row(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(RmSize.iconSm),
        )
        Text(
            text = label,
            style = RmTextStyles.caption,
            color = tint,
            modifier = Modifier.padding(start = RmSpacing.space1),
        )
    }
}

/**
 * 재생성 다이얼로그: 선택적 개선 지시를 받는다(빈 입력 허용 — 그대로 다시 만들기). 확인 시 항목 재생성을 시작한다.
 * 재생성은 장시간(LLM)이라 진행 표시는 카드 상태배지로 보여준다(이 다이얼로그는 시작만 담당).
 */
@Composable
private fun RegenerateDialog(
    onConfirm: (directive: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RmTheme.colors
    var directive by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(RmRadius.card),
        title = { Text(text = "이 항목 다시 만들기", style = RmTextStyles.headingM, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RmSpacing.space3)) {
                Text(
                    text = "이 항목만 AI로 다시 만들어요. 원하는 방향이 있으면 적어 주세요(선택).",
                    style = RmTextStyles.bodyS,
                    color = colors.textSecondary,
                )
                RmTextField(
                    value = directive,
                    onValueChange = { directive = it },
                    label = "개선 지시 (선택)",
                    placeholder = "예: 더 짧게, 성과 수치 강조",
                    singleLine = false,
                    minHeight = RmSize.multilineMinHeight,
                )
            }
        },
        // 다이얼로그 버튼 슬롯은 RowScope가 아니라 fillMaxWidth인 PrimaryButton을 쓰면 폭이 어긋난다.
        // ConfirmDialog와 같은 관용으로 내용 폭 Button(primary)/GhostButton(취소)을 쓴다.
        confirmButton = {
            Button(
                onClick = { onConfirm(directive) },
                shape = RoundedCornerShape(RmRadius.card),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text(
                    text = "다시 만들기",
                    style = RmTextStyles.label.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        dismissButton = {
            GhostButton(text = "취소", onClick = onDismiss, fillWidth = false)
        },
    )
}

@Composable
private fun SectionStatusBadge(section: ArtifactSectionUi) {
    val colors = RmTheme.colors
    when (section.status) {
        SectionStatus.GENERATED ->
            Badge(text = "완료", fg = colors.success, bg = colors.successBg)
        SectionStatus.GENERATION_FAILED ->
            Badge(text = "생성 실패", fg = colors.danger, bg = colors.dangerBg)
        SectionStatus.VALIDATION_FAILED ->
            Badge(text = "검증 실패", fg = colors.danger, bg = colors.dangerBg)
        SectionStatus.GENERATING ->
            StatusBadge(text = "생성 중")
    }
}

private fun failedMessage(section: ArtifactSectionUi): String = when (section.status) {
    SectionStatus.GENERATION_FAILED -> "이 항목을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
    SectionStatus.VALIDATION_FAILED -> "이 항목이 검증을 통과하지 못했어요. 다시 시도해 주세요."
    else -> "이 항목을 표시할 수 없어요."
}
