package watson.resumaker.ui.theme

import androidx.compose.ui.unit.Dp

/**
 * 디자인 시스템 §7 웹 반응형 구간(WX-1). 뷰포트 폭으로 분기한다.
 * - Compact: <600px (모바일) — 단일 컬럼, 좌우 패딩 20dp, 그리드 1열.
 * - Medium: 600~1024px (태블릿) — 가용폭, 좌우 패딩 24dp, 그리드 2열.
 * - Expanded: >1024px (데스크톱) — 콘텐츠 max-width 중앙, 좌우 패딩 32dp, 그리드 2~3열.
 */
enum class WindowSize { COMPACT, MEDIUM, EXPANDED }

/** 뷰포트 폭 → [WindowSize]. 경계는 [RmSize.breakpointCompact]/[RmSize.breakpointExpanded]. */
fun windowSizeFor(maxWidth: Dp): WindowSize = when {
    maxWidth < RmSize.breakpointCompact -> WindowSize.COMPACT
    maxWidth < RmSize.breakpointExpanded -> WindowSize.MEDIUM
    else -> WindowSize.EXPANDED
}

/** 구간별 콘텐츠 좌우 패딩(§7). */
fun WindowSize.pagePadding(): Dp = when (this) {
    WindowSize.COMPACT -> RmSize.pagePaddingCompact
    WindowSize.MEDIUM -> RmSize.pagePaddingMedium
    WindowSize.EXPANDED -> RmSize.pagePaddingExpanded
}

/**
 * 반응형 카드 그리드 열 수(§4.2, WX-5). 카드 최소 셀폭 [RmSize.gridCellMin] 기준이되,
 * 구간별 상한을 둔다: Compact 1열 / Medium 2열 / Expanded 최대 3열.
 * 가용 콘텐츠 폭으로 최소폭 충족 열 수를 계산한 뒤 구간 상한으로 클램프한다.
 */
fun gridColumnsFor(windowSize: WindowSize, contentWidth: Dp): Int {
    val maxByWindow = when (windowSize) {
        WindowSize.COMPACT -> 1
        WindowSize.MEDIUM -> 2
        WindowSize.EXPANDED -> 3
    }
    val byWidth = (contentWidth.value / RmSize.gridCellMin.value).toInt().coerceAtLeast(1)
    return byWidth.coerceAtMost(maxByWindow)
}
