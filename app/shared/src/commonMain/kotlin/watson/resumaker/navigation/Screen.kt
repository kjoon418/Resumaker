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

    /** 이력서 양식 목록. */
    data object TemplateList : Screen

    /**
     * 이력서 양식 생성·수정. [templateId]가 null이면 신규.
     * [presetName]/[presetSections]가 non-null이면 프리셋에서 시작(FU-B).
     */
    data class TemplateEdit(
        val templateId: String? = null,
        val presetName: String? = null,
        val presetSections: List<watson.resumaker.model.dto.SectionResponse>? = null,
    ) : Screen

    /** 프리셋 선택 화면(FU-B). */
    data object TemplatePreset : Screen

    /** 회사 양식 붙여넣기 + 확정 게이트 화면(FU-C). */
    data object TemplateInterpret : Screen

    /**
     * 산출물 생성 진입(종류·경험·목표·양식 선택 → 생성). [hasExperiences]는 더 이상 분기에 쓰지 않으나
     * (생성 화면이 직접 빈 경험을 감지해 예방형 카피로 분기) 기존 진입점 호환을 위해 남긴다.
     */
    data class Artifact(val hasExperiences: Boolean = true) : Screen

    /**
     * 산출물 열람. [artifactId]로 활성 버전을 조회·표시한다.
     * [initial]은 생성 직후 재조회를 피하기 위한 transient 생성 응답(URL 비참여, 딥링크 시 null).
     */
    data class ArtifactView(
        val artifactId: String,
        val initial: watson.resumaker.model.dto.GenerationResponse? = null,
    ) : Screen

    /**
     * 산출물 버전 기록·비교. [artifactId]의 모든 버전을 생성순으로 보여주고, 두 버전을 골라 definitionKey로
     * 같은 항목을 맞춰 비교하며, 한 버전을 골라 복원(활성 전환)한다(§271~290·§363).
     */
    data class ArtifactVersions(val artifactId: String) : Screen

    /** 마이페이지. */
    data object MyPage : Screen
}
