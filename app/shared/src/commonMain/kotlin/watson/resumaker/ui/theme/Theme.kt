package watson.resumaker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 디자인 시스템 §2.4 Material3 ColorScheme 매핑(light only — 다크는 MVP 범위 밖).
 */
private val RmLightColorScheme = lightColorScheme(
    primary = RmPalette.primary,
    onPrimary = RmPalette.onPrimary,
    primaryContainer = RmPalette.primaryContainer,
    onPrimaryContainer = RmPalette.onPrimaryContainer,
    secondary = RmPalette.textSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = RmPalette.borderSubtle,
    onSecondaryContainer = RmPalette.textLabel,
    background = RmPalette.background,
    onBackground = RmPalette.textPrimary,
    surface = RmPalette.surface,
    onSurface = RmPalette.textPrimary,
    surfaceVariant = RmPalette.surfaceSubtle,
    onSurfaceVariant = RmPalette.textSecondary,
    outline = RmPalette.border,
    outlineVariant = RmPalette.borderSubtle,
    error = RmPalette.danger,
    onError = Color(0xFFFFFFFF),
    errorContainer = RmPalette.dangerBg,
    onErrorContainer = RmPalette.onErrorContainer,
)

/** 브랜드 고유 색 토큰 CompositionLocal(§0 권장 패턴). */
val LocalRmColors: ProvidableCompositionLocal<RmColors> = staticCompositionLocalOf { RmColors() }

/**
 * 앱 루트 테마. Material3를 베이스로 깔고, Material 슬롯에 안 맞는 토큰을 [LocalRmColors]로 추가 노출한다.
 * Pretendard 폰트(§3.1)를 로드해 텍스트 스타일 묶음을 [LocalRmTextStyles]로 제공하고 Material typography에도 적용한다.
 */
@Composable
fun RmTheme(content: @Composable () -> Unit) {
    val textStyles = rmTextStyleSet(rememberPretendardFamily())
    CompositionLocalProvider(
        LocalRmColors provides RmColors(),
        LocalRmTextStyles provides textStyles,
    ) {
        MaterialTheme(
            colorScheme = RmLightColorScheme,
            typography = textStyles.typography,
            content = content,
        )
    }
}

/** 화면/컴포넌트에서 브랜드 토큰에 접근하는 진입점. */
object RmTheme {
    val colors: RmColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRmColors.current
}
