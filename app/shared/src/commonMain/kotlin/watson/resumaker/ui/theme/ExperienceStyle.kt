package watson.resumaker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import watson.resumaker.model.type.ExperienceType

/**
 * 디자인 시스템 §6 경험유형 5종 매핑(라벨·액센트 전경/배경·아이콘).
 * 색은 §6 표 그대로(blue/emerald/rose/amber/orange), 아이콘은 [RmIcons]로 대체 매핑.
 */
data class ExperienceStyle(
    val type: ExperienceType,
    val label: String,
    val fg: Color,
    val bg: Color,
    val icon: ImageVector,
)

/** §6 표의 5종 스타일. UI에서 칩/배지 렌더에 사용한다. */
fun ExperienceType.style(): ExperienceStyle = when (this) {
    ExperienceType.PROJECT -> ExperienceStyle(
        type = this,
        label = "프로젝트",
        fg = Color(0xFF2563EB),  // blue-600
        bg = Color(0xFFEFF6FF),  // blue-50
        icon = RmIcons.Code,
    )
    ExperienceType.JOB -> ExperienceStyle(
        type = this,
        label = "직무·인턴",
        fg = Color(0xFF059669),  // emerald-600
        bg = Color(0xFFECFDF5),  // emerald-50
        icon = RmIcons.Work,
    )
    ExperienceType.EXTRACURRICULAR -> ExperienceStyle(
        type = this,
        label = "대외활동",
        fg = Color(0xFFE11D48),  // rose-600
        bg = Color(0xFFFFF1F2),  // rose-50
        icon = RmIcons.Groups,
    )
    ExperienceType.AWARD -> ExperienceStyle(
        type = this,
        label = "수상·자격",
        fg = Color(0xFFD97706),  // amber-600
        bg = Color(0xFFFFFBEB),  // amber-50
        icon = RmIcons.Trophy,
    )
    ExperienceType.LEARNING -> ExperienceStyle(
        type = this,
        label = "학습·교육",
        fg = Color(0xFFEA580C),  // orange-600
        bg = Color(0xFFFFF7ED),  // orange-50
        icon = RmIcons.School,
    )
}

/** 칩 토글·배지 렌더 순서(§8.4 [P][J][E][A][L]). */
val experienceTypesInOrder: List<ExperienceType> = listOf(
    ExperienceType.PROJECT,
    ExperienceType.JOB,
    ExperienceType.EXTRACURRICULAR,
    ExperienceType.AWARD,
    ExperienceType.LEARNING,
)
