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
     * 목표 상세(회사·직무·채용 방향 원문 + AI 작성 전략 열람·상태·재시도). 목록 카드 클릭 시 진입.
     */
    data class TargetDetail(val targetId: String) : Screen

    /**
     * 목표 생성·수정. [targetId]가 null이면 신규(`/targets/new`), non-null이면 수정(`/targets/{id}/edit`).
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
     *
     * [prefillJob]은 입력 관련 실패 작업의 '경험 다시 고르기'(EDIT_INPUTS) 진입 시, 실패 작업이 보관한 입력
     * (종류·경험·목표·양식)을 미리 채우기 위한 transient 데이터다(URL 비참여, 딥링크 시 null). 생성 화면이
     * 성공 제출하면 이 작업(jobId)을 삭제해 잔존 실패 기록을 정리한다.
     */
    data class Artifact(
        val hasExperiences: Boolean = true,
        val prefillJob: watson.resumaker.model.dto.GenerationJobResponse? = null,
    ) : Screen

    /** 내 산출물 목록(이력서·포트폴리오). 진행 중/실패 생성 작업과 완성 산출물을 함께 보여주고 폴링한다. */
    data object ArtifactList : Screen

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

    /**
     * 품질 점검·개선(RESUME 전용, QC10). 1단계(소견 확인)와 2단계(후보 비교·채택)를 동일 화면 스택 안에서
     * 처리한다. ViewModel을 공유해 상태를 이어받는다.
     * [artifactId]로 품질 점검을 요청하고 개선 결과를 채택한다.
     */
    data class ArtifactQualityReview(val artifactId: String) : Screen

    /** 마이페이지. */
    data object MyPage : Screen
}
