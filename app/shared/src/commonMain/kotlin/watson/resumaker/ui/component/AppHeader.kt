package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.WindowSize
import watson.resumaker.ui.theme.pressScale

/**
 * 헤더 내비 목적지(WX-7). 마이는 우측 계정 메뉴로 분리하므로 탭에서 제외.
 *
 * IA 검토 필요: 만들기·산출물을 더해 탭이 6개가 됐다. 탭 6개의 배치·라벨·우선순위와 Compact 구간
 * 수용 가능성은 최종 디자이너 리뷰 대상이다. 우선 가장 일관된 형태(홈/경험/목표/양식/만들기/산출물 순)로 구현한다.
 */
enum class HeaderTab(val label: String) {
    HOME("홈"),
    EXPERIENCE("경험"),
    TARGET("목표"),
    TEMPLATE("양식"),
    CREATE("만들기"),
    ARTIFACT("산출물"),
}

/**
 * 디자인 시스템 §5.6/§7 AppHeader(WX-7/9). 전체폭 64dp 웹 헤더.
 * 좌측 로고 + 중앙(좌측 정렬) 홈/경험/목표 탭 + 우측 끝 계정(마이) 아이콘.
 * 콘텐츠는 [LocalContentMaxWidth](본문 폭)로 중앙 제한해 본문 컨테이너와 정렬을 맞춘다.
 * 헤더 막대 자체는 전체폭 surface + 하단 헤어라인 보더.
 *
 * [selected]가 null이면(세션 등) 탭 강조 없음. Compact 구간에서는 탭 라벨을 숨기지 않고
 * 좁은 간격으로 노출(MVP는 상단 내비 통일, 바텀내비 폴백은 별도).
 */
@Composable
fun AppHeader(
    selected: HeaderTab?,
    onSelectTab: (HeaderTab) -> Unit,
    onOpenAccount: () -> Unit,
    windowSize: WindowSize,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    val contentMaxWidth = LocalContentMaxWidth.current
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .height(RmSize.headerHeight)
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Resumaker",
                    style = RmTextStyles.headingS.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary,
                    // 로고 클릭 → 홈. 별도 콜백을 더하지 않고 홈 탭 선택과 동일 경로(switchRoot(Home))를 재사용한다.
                    modifier = Modifier
                        .pressScale(onClick = { onSelectTab(HeaderTab.HOME) })
                        .semantics { contentDescription = "홈으로" },
                )
                // M-6: 탭이 4개(홈/경험/목표/양식)로 늘어 Compact(<600px)에서 오버플로 위험이 있다.
                // Compact에서는 탭 행을 가로 스크롤 가능하게 해 라벨 잘림 없이 모든 탭을 노출한다
                // (Medium/Expanded는 폭이 충분하므로 스크롤 없이 기존 레이아웃 유지 — 회귀 없음).
                val isCompact = windowSize == WindowSize.COMPACT
                val tabsBase = Modifier
                    .weight(1f)
                    .padding(start = if (isCompact) RmSpacing.space4 else RmSpacing.space6)
                Row(
                    modifier = if (isCompact) {
                        tabsBase.horizontalScroll(rememberScrollState())
                    } else {
                        tabsBase
                    },
                    horizontalArrangement = Arrangement.spacedBy(
                        if (isCompact) RmSpacing.space2 else RmSpacing.space5,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderTab.entries.forEach { tab ->
                        HeaderNavItem(
                            label = tab.label,
                            active = tab == selected,
                            onClick = { onSelectTab(tab) },
                        )
                    }
                }
                IconButton(
                    onClick = onOpenAccount,
                    modifier = Modifier.semantics { contentDescription = "계정 메뉴" },
                ) {
                    Icon(
                        imageVector = RmIcons.Person,
                        contentDescription = "마이페이지",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(RmSize.iconNav),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(RmSize.hairline).background(colors.borderSubtle))
    }
}

/** 헤더 내비 단일 탭. 활성 primary+Bold, 비활성 textSecondary. 웹 hover/press 피드백. */
@Composable
private fun HeaderNavItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = RmTheme.colors
    Box(
        modifier = Modifier
            .pressScale(onClick = onClick)
            .padding(horizontal = RmSpacing.space2, vertical = RmSpacing.space1)
            .then(
                if (active) {
                    Modifier.background(colors.primaryContainer, RoundedCornerShape(RmRadius.sm))
                        .padding(horizontal = RmSpacing.space2, vertical = RmSpacing.space1)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RmTextStyles.bodyS.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (active) colors.primary else colors.textSecondary,
        )
    }
}

@Preview
@Composable
private fun AppHeaderPreview() {
    RmTheme {
        AppHeader(
            selected = HeaderTab.HOME,
            onSelectTab = {},
            onOpenAccount = {},
            windowSize = WindowSize.EXPANDED,
            horizontalPadding = RmSpacing.space8,
        )
    }
}
