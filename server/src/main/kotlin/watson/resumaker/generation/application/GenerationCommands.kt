package watson.resumaker.generation.application

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
