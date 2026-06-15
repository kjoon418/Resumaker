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
 * - `/artifact`          → Artifact (transient: hasExperiences 는 URL 비참여, 진입 시 소스에서 공급)
 * - `/me`                → MyPage
 *
 * **Transient 화면 (CQ-4):** [Screen.Artifact.hasExperiences]는 URL에 저장하지 않는다.
 * 홈·목록에서 진입 시 ViewModel이 계산한 값을 [Screen.Artifact] 생성자에 전달해 push한다(App.kt 참조).
 * `/artifact` 를 직접 방문·새로고침하면 기본값 `true` 로 렌더링되며, "준비 중" 화면이라
 * 의미 있는 딥링크 목적지가 아니므로 boolean URL 직렬화는 과도한 복잡성이다.
 */
object Routes {

    /**
     * 화면 → 경로 문자열.
     * Artifact 는 transient(CQ-4): [Screen.Artifact.hasExperiences] 를 URL 에 포함하지 않는다.
     */
    fun pathOf(screen: Screen): String = when (screen) {
        Screen.Session -> "/session"
        Screen.Home -> "/"
        Screen.ExperienceList -> "/experiences"
        is Screen.ExperienceEdit -> if (screen.experienceId == null) "/experiences/new" else "/experiences/${screen.experienceId}"
        Screen.TargetList -> "/targets"
        is Screen.TargetEdit -> if (screen.targetId == null) "/targets/new" else "/targets/${screen.targetId}"
        Screen.TemplateList -> "/resume-templates"
        is Screen.TemplateEdit -> if (screen.templateId == null) "/resume-templates/new" else "/resume-templates/${screen.templateId}"
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
            segments == listOf("resume-templates") -> Screen.TemplateList
            segments.size == 2 && segments[0] == "resume-templates" ->
                if (segments[1] == "new") Screen.TemplateEdit(null) else Screen.TemplateEdit(segments[1])
            segments == listOf("artifact") -> Screen.Artifact()
            segments == listOf("me") -> Screen.MyPage
            else -> null
        }
    }
}
