package watson.resumaker.navigation

/**
 * 화면 목적지. navigation-compose 의존을 더하지 않고, sealed + 상태 기반 라우팅으로 단순하게 구성한다
 * (브리프 §navigation: 의존성 추가 어려우면 후자로 단순하게).
 */
sealed interface Screen {
    /** 세션 진입(가입 / userId 재진입). */
    data object Session : Screen

    /** 홈 대시보드. */
    data object Home : Screen

    /** 경험 목록. */
    data object ExperienceList : Screen

    /**
     * 경험 생성·수정. [experienceId]가 null이면 신규.
     */
    data class ExperienceEdit(val experienceId: String? = null) : Screen

    /** 목표 목록. */
    data object TargetList : Screen

    /**
     * 목표 생성·수정. [targetId]가 null이면 신규.
     */
    data class TargetEdit(val targetId: String? = null) : Screen

    /**
     * 산출물 "준비 중". [hasExperiences]가 false면 빈 경험묶음 예방형 카피로 분기한다(수용기준 8).
     */
    data class Artifact(val hasExperiences: Boolean = true) : Screen

    /** 마이페이지. */
    data object MyPage : Screen
}
