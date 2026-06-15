package watson.resumaker.navigation

/**
 * 화면 ↔ URL 경로 매핑(WX-8). 브라우저 History API 연동으로 back/forward·새로고침·딥링크를 지원한다.
 * 새 의존성 없이(navigation 라이브러리 미도입) 순수 함수로 변환한다.
 *
 * 경로 규칙:
 * - `/`                  → Home
 * - `/session`           → Session
 * - `/experiences`       → ExperienceList
 * - `/experiences/new`   → ExperienceEdit(null)
 * - `/experiences/{id}`  → ExperienceEdit(id)
 * - `/targets`           → TargetList
 * - `/targets/new`       → TargetEdit(null)
 * - `/targets/{id}`      → TargetEdit(id)
 * - `/artifact`          → Artifact
 * - `/me`                → MyPage
 */
object Routes {

    /** 화면 → 경로 문자열. */
    fun pathOf(screen: Screen): String = when (screen) {
        Screen.Session -> "/session"
        Screen.Home -> "/"
        Screen.ExperienceList -> "/experiences"
        is Screen.ExperienceEdit -> if (screen.experienceId == null) "/experiences/new" else "/experiences/${screen.experienceId}"
        Screen.TargetList -> "/targets"
        is Screen.TargetEdit -> if (screen.targetId == null) "/targets/new" else "/targets/${screen.targetId}"
        is Screen.Artifact -> "/artifact"
        Screen.MyPage -> "/me"
    }

    /**
     * 경로 문자열 → 화면. 알 수 없는 경로는 null(호출자가 세션/홈 등으로 폴백).
     * 쿼리·해시는 무시하고 경로만 본다.
     */
    fun screenOf(path: String): Screen? {
        val clean = path.substringBefore('?').substringBefore('#').trim()
        val segments = clean.trim('/').split('/').filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> Screen.Home
            segments == listOf("session") -> Screen.Session
            segments == listOf("experiences") -> Screen.ExperienceList
            segments.size == 2 && segments[0] == "experiences" ->
                if (segments[1] == "new") Screen.ExperienceEdit(null) else Screen.ExperienceEdit(segments[1])
            segments == listOf("targets") -> Screen.TargetList
            segments.size == 2 && segments[0] == "targets" ->
                if (segments[1] == "new") Screen.TargetEdit(null) else Screen.TargetEdit(segments[1])
            segments == listOf("artifact") -> Screen.Artifact()
            segments == listOf("me") -> Screen.MyPage
            else -> null
        }
    }
}
