package watson.resumaker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import resumaker.app.shared.generated.resources.Res
import resumaker.app.shared.generated.resources.pretendard_bold
import resumaker.app.shared.generated.resources.pretendard_medium
import resumaker.app.shared.generated.resources.pretendard_regular
import resumaker.app.shared.generated.resources.pretendard_semibold

/**
 * 디자인 시스템 §3 타이포그래피.
 *
 * 폰트(§3.1): Pretendard 4종(Regular/Medium/SemiBold/Bold)을
 * `app/shared/src/commonMain/composeResources/font/`에 번들하고 Compose Resources(`Res.font.*`)로 로드한다.
 * Compose Resources의 `Font(...)`는 `@Composable`이므로 [rememberPretendardFamily]에서 FontFamily를 구성하고,
 * [RmTheme]가 이를 [RmTextStyleSet]으로 만들어 [LocalRmTextStyles]로 노출한다.
 * 미로딩 시 Compose가 §3.1 fallback(시스템 산세리프)로 자연스럽게 떨어진다.
 */
@Composable
fun rememberPretendardFamily(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, FontWeight.Normal),
    Font(Res.font.pretendard_medium, FontWeight.Medium),
    Font(Res.font.pretendard_semibold, FontWeight.SemiBold),
    Font(Res.font.pretendard_bold, FontWeight.Bold),
)

private fun rmStyle(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
): TextStyle =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

/**
 * §3.2 타입 스케일 + §5 보조 토큰(bodyM·captionBold)을 한 [family] 기준으로 구성한 묶음.
 * Material3 [typography]와 의미 기반 별칭을 함께 들고 있어, 테마가 단일 출처로 제공한다.
 */
data class RmTextStyleSet(
    val displayL: TextStyle,
    val titleL: TextStyle,
    val headingM: TextStyle,
    val headingS: TextStyle,
    val bodyL: TextStyle,
    val bodyMr: TextStyle,
    val bodyS: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    /** 카드 아이템 제목(bodyM, 15sp SemiBold). */
    val bodyM: TextStyle,
    /** 배지/태그 텍스트(captionBold, 12sp Bold). */
    val captionBold: TextStyle,
) {
    /** Material3 슬롯 매핑(§3.3). MaterialTheme(typography=...)에 그대로 넣는다. */
    val typography: Typography = Typography(
        displaySmall = displayL,
        titleLarge = titleL,
        titleMedium = headingM,
        titleSmall = headingS,
        bodyLarge = bodyL,
        bodyMedium = bodyMr,
        bodySmall = bodyS,
        labelLarge = label,   // 버튼은 컴포넌트에서 Bold 오버라이드
        labelMedium = caption,
        labelSmall = overline,
    )
}

/** [family] 기준으로 디자인 시스템 텍스트 스타일 묶음을 만든다(§3.2~§3.3). */
fun rmTextStyleSet(family: FontFamily): RmTextStyleSet = RmTextStyleSet(
    displayL = rmStyle(family, FontWeight.Bold, 24, 32),
    titleL = rmStyle(family, FontWeight.Bold, 20, 28),
    headingM = rmStyle(family, FontWeight.Bold, 18, 26),
    headingS = rmStyle(family, FontWeight.Bold, 17, 24),
    bodyL = rmStyle(family, FontWeight.Normal, 16, 24),
    bodyMr = rmStyle(family, FontWeight.Normal, 15, 22),
    bodyS = rmStyle(family, FontWeight.Medium, 14, 20),
    label = rmStyle(family, FontWeight.SemiBold, 14, 20),
    caption = rmStyle(family, FontWeight.Medium, 12, 16),
    overline = rmStyle(family, FontWeight.SemiBold, 11, 14, letterSpacing = 0.5),
    bodyM = rmStyle(family, FontWeight.SemiBold, 15, 22),
    captionBold = rmStyle(family, FontWeight.Bold, 12, 16),
)

/**
 * 텍스트 스타일 묶음 CompositionLocal. [RmTheme]가 Pretendard 기준 묶음을 provide한다.
 * 기본값은 SansSerif fallback(테마 밖/프리뷰 안전).
 */
val LocalRmTextStyles: ProvidableCompositionLocal<RmTextStyleSet> =
    staticCompositionLocalOf { rmTextStyleSet(FontFamily.SansSerif) }

/**
 * 화면/컴포넌트에서 텍스트 스타일에 접근하는 진입점.
 * 기존 호출부(`RmTextStyles.bodyM` 등)를 그대로 유지하되, 값을 테마(Pretendard)에서 가져온다.
 */
object RmTextStyles {
    val displayL: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.displayL
    val titleL: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.titleL
    val headingM: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.headingM
    val headingS: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.headingS
    val bodyL: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.bodyL
    val bodyMr: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.bodyMr
    val bodyS: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.bodyS
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.label
    val caption: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.caption
    val overline: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.overline
    val bodyM: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.bodyM
    val captionBold: TextStyle @Composable @ReadOnlyComposable get() = LocalRmTextStyles.current.captionBold
}
