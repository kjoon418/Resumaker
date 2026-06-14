package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.11 ComingSoon — 산출물 생성(백엔드 미구현)의 정직성 컴포넌트.
 * 가짜 동작 금지. 기록→겨냥 흐름으로 회수하는 유도 버튼을 제공한다.
 *
 * @param hasExperiences 경험이 1건이라도 있는지. false면 "빈 경험묶음" 예방형 카피로 분기해
 *   "먼저 경험을 기록하세요"를 강조한다(수용기준 8, 도메인 §110·§410).
 * @param onRecordExperience "경험 기록하기" 유도(Primary).
 * @param onAddTarget "목표 추가하기" 유도(Secondary).
 */
@Composable
fun ComingSoon(
    onRecordExperience: () -> Unit,
    onAddTarget: () -> Unit,
    modifier: Modifier = Modifier,
    hasExperiences: Boolean = true,
) {
    val colors = RmTheme.colors
    // 빈 경험묶음일 때는 산출물 안내보다 "먼저 경험 기록"을 예방형으로 안내(수용기준 8).
    val title = if (hasExperiences) "곧 제공됩니다" else "먼저 경험을 기록해 주세요"
    val description = if (hasExperiences) {
        "경험과 목표를 충실히 쌓아두면, 준비되는 대로 최적의 이력서·포트폴리오를 만들어 드려요."
    } else {
        "이력서·포트폴리오는 기록한 경험을 재료로 만들어요. 아직 경험이 없으니, 먼저 한 가지라도 기록해 볼까요?"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RmSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RmSpacing.space3),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = RmIcons.Sparkles,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(text = title, style = RmTextStyles.headingM, color = colors.textPrimary, textAlign = TextAlign.Center)
        StatusBadge(text = "준비 중")
        Text(
            text = description,
            style = RmTextStyles.bodyS,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(RmSpacing.space2))
        PrimaryButton(text = "경험 기록하기", onClick = onRecordExperience)
        SecondaryButton(text = "목표 추가하기", onClick = onAddTarget)
    }
}

@Preview
@Composable
private fun ComingSoonPreview() {
    RmTheme {
        ComingSoon(onRecordExperience = {}, onAddTarget = {}, hasExperiences = false)
    }
}
