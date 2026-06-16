package watson.resumaker.generation.application

import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.template.domain.ResumeTemplateId

/**
 * 1차 생성 유스케이스 입력 커맨드(이미 VO로 변환된 상태 — 컨트롤러 Mapper가 만든다, Cycle D).
 * Service는 VO만 다룬다(구현 설계 §8).
 *
 * @param experienceIds 생성에 쓸 경험 묶음 식별자(빈 묶음 거부 — 수용 기준 8).
 * @param targetId      목표 정보 식별자(채용 방향 필수).
 * @param templateId    지정 양식 식별자. 이번 사이클은 '양식 필수'다(미지정 시 AI 생성 양식은 다음 사이클 범위).
 */
data class GenerateResumeCommand(
    val experienceIds: List<ExperienceRecordId>,
    val targetId: watson.resumaker.target.domain.TargetBriefId,
    val templateId: ResumeTemplateId,
)

/**
 * 포트폴리오 1차 생성 커맨드. 포트폴리오는 양식이 없고 선택 경험당 서사 1개를 만든다(도메인 이해 §357).
 *
 * @param experienceIds 선택 경험 묶음 식별자(빈 묶음 거부 — 수용 기준 8). 경험당 항목 1개로 1:1 대응.
 * @param targetId      목표 정보 식별자(채용 방향 필수).
 */
data class GeneratePortfolioCommand(
    val experienceIds: List<ExperienceRecordId>,
    val targetId: watson.resumaker.target.domain.TargetBriefId,
)

/**
 * 항목 단위 재생성 유스케이스 입력 커맨드(도메인 이해 §5 개선/재생성, 구현 설계 §11 태스크 5).
 *
 * 재생성은 직전 활성 버전을 복제한 위에 **이 항목만** AI로 다시 만들어 교체한 새 버전을 만든다(수용 기준 10·19).
 * 목표는 산출물의 불변 스냅샷([watson.resumaker.artifact.domain.Artifact.targetSnapshot])에서 읽으므로
 * 커맨드에 목표 식별자를 포함하지 않는다(§347·§364: 목표 변경 = 새 산출물).
 *
 * @param artifactId 재생성 대상 산출물(소유 격리).
 * @param sectionId  활성 버전에서 재생성할 항목.
 * @param directive  선택적 개선 지시(§268). "더 짧게"·"성과 수치 강조" 등. 근거 없는 사실 추가 요구는 거부된다(§284).
 */
data class RegenerateSectionCommand(
    val artifactId: ArtifactId,
    val sectionId: SectionId,
    val directive: String?,
)
