package watson.resumaker.feature.target

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import watson.resumaker.model.type.StrategyStatus
import watson.resumaker.ui.component.StatusBadge
import watson.resumaker.ui.theme.RmTheme

/**
 * 목표의 AI 작성 전략 상태 배지(목록 카드·생성 진입 행에서 공용). 상태별 색 쌍·문구를 한 곳에 모은다.
 * - PENDING/EXTRACTING → "전략 분석 중"(warning)
 * - READY → "전략 완료"(success)
 * - FAILED → "전략 없음"(danger)
 *
 * [compact]가 true면 생성 진입 행 제목 옆 작은 배지용 짧은 문구("분석 중")를 쓴다.
 */
@Composable
fun StrategyStatusBadge(
    status: StrategyStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = RmTheme.colors
    when (status) {
        StrategyStatus.PENDING, StrategyStatus.EXTRACTING -> StatusBadge(
            text = if (compact) "분석 중" else "전략 분석 중",
            fg = colors.warning,
            bg = colors.warningBg,
            modifier = modifier,
        )
        StrategyStatus.READY -> StatusBadge(
            text = "전략 완료",
            fg = colors.success,
            bg = colors.successBg,
            modifier = modifier,
        )
        StrategyStatus.FAILED -> StatusBadge(
            text = "전략 없음",
            fg = colors.danger,
            bg = colors.dangerBg,
            modifier = modifier,
        )
    }
}
