package watson.resumaker.template.application

import org.springframework.stereotype.Component
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.domain.TemplatePreset

/**
 * 서비스 제공 표준 이력서 양식 프리셋(도메인 이해 §2.5, 도메인 열린 질문 #4).
 *
 * 현재 3종을 시드로 제공한다. 도메인 열린 질문 #4: "기본 프리셋 양식의 종류·개수·구성"은
 * 서비스 오너 큐레이션 판단이 필요하다. 종류·구성이 결정되면 이 목록을 수정하거나
 * DB/외부 설정으로 교체한다.
 *
 * 프리셋은 사용자 소유가 아니므로 영속화하지 않는다.
 */
@Component
class TemplatePresetProvider {

    fun getAll(): List<TemplatePreset> = PRESETS

    fun findByKey(key: String): TemplatePreset? = PRESETS.find { it.key == key }

    companion object {
        private val PRESETS = listOf(
            TemplatePreset(
                key = "standard-new-grad",
                name = "신입 개발자 표준",
                sections = listOf(
                    SectionDefinition.of("한 줄 자기소개", SectionCharacter.SUMMARY, required = true),
                    SectionDefinition.of("핵심 역량", SectionCharacter.SUMMARY, required = false),
                    SectionDefinition.of("주요 프로젝트", SectionCharacter.CAREER, required = true),
                    SectionDefinition.of("학력·교육", SectionCharacter.SUMMARY, required = false),
                ),
            ),
            TemplatePreset(
                key = "project-focused",
                name = "프로젝트 중심",
                sections = listOf(
                    SectionDefinition.of("프로젝트 요약", SectionCharacter.SUMMARY, required = true),
                    SectionDefinition.of("주요 프로젝트 1", SectionCharacter.CAREER, required = true),
                    SectionDefinition.of("주요 프로젝트 2", SectionCharacter.CAREER, required = false),
                    SectionDefinition.of("사용 기술 스택", SectionCharacter.SUMMARY, required = false),
                ),
            ),
            TemplatePreset(
                key = "career-summary",
                name = "경력 요약형",
                sections = listOf(
                    SectionDefinition.of("경력 요약", SectionCharacter.SUMMARY, required = true),
                    SectionDefinition.of("주요 경력 1", SectionCharacter.CAREER, required = true),
                    SectionDefinition.of("주요 경력 2", SectionCharacter.CAREER, required = false),
                    SectionDefinition.of("보유 역량", SectionCharacter.SUMMARY, required = false),
                ),
            ),
        )
    }
}
