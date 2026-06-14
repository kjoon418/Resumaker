package watson.resumaker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 디자인 시스템 §2 색상 토큰. 화면 코드는 raw hex 대신 [RmColors] 또는 Material ColorScheme만 참조한다.
 *
 * Material3 스킴 슬롯에 1:1로 매핑되지 않는 브랜드 고유 토큰(경험유형 액센트, surface 단계, status 등)은
 * [RmColors]로 묶어 CompositionLocal([watson.resumaker.ui.theme.LocalRmColors])로 노출한다.
 */
object RmPalette {
    // 2.2 핵심 팔레트 (Tailwind slate/blue)
    val primary = Color(0xFF2563EB)           // blue-600
    val primaryPressed = Color(0xFF1D4ED8)    // blue-700
    val primaryContainer = Color(0xFFEFF6FF)  // blue-50
    val onPrimaryContainer = Color(0xFF1D4ED8) // blue-700
    val primaryBorder = Color(0xFFDBEAFE)     // blue-100
    val onPrimary = Color(0xFFFFFFFF)

    val background = Color(0xFFF8FAFC)        // slate-50
    val surface = Color(0xFFFFFFFF)
    val surfaceSubtle = Color(0xFFF8FAFC)     // slate-50 (입력칸/옅은 표면)
    val border = Color(0xFFE2E8F0)            // slate-200
    val borderSubtle = Color(0xFFF1F5F9)      // slate-100

    val textPrimary = Color(0xFF0F172A)       // slate-900
    val textSecondary = Color(0xFF64748B)     // slate-500
    val textTertiary = Color(0xFF94A3B8)      // slate-400
    val textLabel = Color(0xFF334155)         // slate-700
    val textBody = Color(0xFF475569)          // slate-600

    // 2.3 상태색
    val success = Color(0xFF059669)
    val successBg = Color(0xFFECFDF5)
    val warning = Color(0xFFD97706)
    val warningBg = Color(0xFFFFFBEB)
    val danger = Color(0xFFEF4444)
    val dangerBg = Color(0xFFFEF2F2)
    val dangerText = Color(0xFFF87171)        // red-400 (비파괴 표기용)
    val info = Color(0xFF2563EB)
    val infoBg = Color(0xFFEFF6FF)
    val onErrorContainer = Color(0xFFB91C1C)  // red-700

    // 입력 위 보조 표면
    val surfaceDisabled = Color(0xFFF1F5F9)   // slate-100 (disabled 입력 배경)
}

/**
 * 브랜드 고유 색 토큰 묶음. Material ColorScheme로 표현하기 어려운 토큰을 담는다.
 * 화면/컴포넌트는 `RmTheme.colors`(= LocalRmColors.current)로 접근한다.
 */
data class RmColors(
    val primary: Color = RmPalette.primary,
    val primaryPressed: Color = RmPalette.primaryPressed,
    val primaryContainer: Color = RmPalette.primaryContainer,
    val onPrimaryContainer: Color = RmPalette.onPrimaryContainer,
    val primaryBorder: Color = RmPalette.primaryBorder,
    val onPrimary: Color = RmPalette.onPrimary,
    val background: Color = RmPalette.background,
    val surface: Color = RmPalette.surface,
    val surfaceSubtle: Color = RmPalette.surfaceSubtle,
    val surfaceDisabled: Color = RmPalette.surfaceDisabled,
    val border: Color = RmPalette.border,
    val borderSubtle: Color = RmPalette.borderSubtle,
    val textPrimary: Color = RmPalette.textPrimary,
    val textSecondary: Color = RmPalette.textSecondary,
    val textTertiary: Color = RmPalette.textTertiary,
    val textLabel: Color = RmPalette.textLabel,
    val textBody: Color = RmPalette.textBody,
    val success: Color = RmPalette.success,
    val successBg: Color = RmPalette.successBg,
    val warning: Color = RmPalette.warning,
    val warningBg: Color = RmPalette.warningBg,
    val danger: Color = RmPalette.danger,
    val dangerBg: Color = RmPalette.dangerBg,
    val dangerText: Color = RmPalette.dangerText,
    val info: Color = RmPalette.info,
    val infoBg: Color = RmPalette.infoBg,
    /** 투명(세그먼트 미선택 배경 등). raw Color.Transparent 누수를 막기 위한 토큰. */
    val transparent: Color = Color.Transparent,
)
