package watson.resumaker.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 디자인 시스템 §4.1 Spacing (4dp 배수). 화면 코드는 raw dp 대신 이 토큰만 참조한다
 * (레이아웃 산식 — 앱 컬럼 폭 등 — 은 예외).
 */
object RmSpacing {
    /** 0.5 스텝(2dp). 배지 수직 패딩 등 미세 간격. */
    val space0_5: Dp = 2.dp
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 20.dp
    val space6: Dp = 24.dp
    val space8: Dp = 32.dp
    val space10: Dp = 40.dp

    /** 화면 표준 콘텐츠 가로 패딩(§4.1: 기본 20dp로 통일). */
    val contentPadding: Dp = space5
}

/**
 * 디자인 시스템 §4.3 Radius.
 */
object RmRadius {
    val sm: Dp = 8.dp        // 작은 배지/select
    val segment: Dp = 10.dp  // 세그먼트 토글 선택 알약(트랙 16 안쪽)
    val chip: Dp = 12.dp     // 아이콘칩(경험유형)
    val card: Dp = 16.dp     // 카드·입력·버튼 기본
    val full: Dp = 9999.dp   // 알약형 태그·둥근 점
}

/**
 * 디자인 시스템 §4.4 Elevation / Shadow.
 */
object RmElevation {
    val card: Dp = 1.dp      // shadow-sm
    val button: Dp = 4.dp    // Primary 버튼(blue tint)
    val sheet: Dp = 8.dp     // 하단 고정 액션바/스낵바
}

/**
 * 디자인 시스템 §4.5 Motion.
 */
object RmMotion {
    const val pressScale: Float = 0.98f
    const val pressScaleStrong: Float = 0.96f
    const val durFast: Int = 120
    const val durBase: Int = 200
}

/**
 * 컴포넌트 표준 치수(디자인 시스템 §5).
 */
object RmSize {
    val controlHeight: Dp = 56.dp     // 입력·Primary 버튼 (h-14)
    val selectHeight: Dp = 52.dp      // SelectField
    val iconChip: Dp = 48.dp          // 경험유형 아이콘칩
    val iconChipSmall: Dp = 40.dp
    val topBarHeight: Dp = 56.dp
    val bottomNavHeight: Dp = 80.dp
    val multilineMinHeight: Dp = 120.dp
    val targetBodyMinHeight: Dp = 140.dp
    val appColumnWidth: Dp = 440.dp   // §7 앱 컬럼 기본 폭
    val appColumnMax: Dp = 480.dp
    val appColumnMin: Dp = 360.dp

    // 아이콘 크기 토큰(MINOR-8: 화면 곳곳의 raw 12/16/18/20/22dp 흡수).
    val iconXs: Dp = 12.dp     // 태그 x·AddChip + 아이콘
    val spinnerSm: Dp = 16.dp  // 버튼 인라인 로딩 인디케이터
    val iconSm: Dp = 18.dp     // 리스트 trailing/삭제·복사·작은 토글 아이콘
    val iconMd: Dp = 20.dp     // 탑바 back·인포/에러 아이콘·텍스트필드 eye·카드 chevron
    val iconNav: Dp = 22.dp    // 바텀내비·유형선택 칩 아이콘

    /** 1dp 헤어라인 보더/디바이더. */
    val hairline: Dp = 1.dp

    /** 스낵바 하단 띄움 간격. */
    val snackbarBottomGap: Dp = 8.dp

    /** 세그먼트 토글 높이. */
    val segmentHeight: Dp = 44.dp

    /** 스켈레톤 리스트 아이템(§5.9): 카드 높이·텍스트 라인 높이. */
    val skeletonItemHeight: Dp = 76.dp
    val skeletonLineLg: Dp = 16.dp     // 제목 라인
    val skeletonLineSm: Dp = 12.dp     // 메타 라인
    val skeletonTitleWidth: Dp = 160.dp
    val skeletonMetaWidth: Dp = 100.dp
}
