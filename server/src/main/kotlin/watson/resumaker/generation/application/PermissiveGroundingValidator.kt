package watson.resumaker.generation.application

import org.springframework.stereotype.Component

/**
 * [GroundingValidator]의 Cycle B 기본 구현. 모든 항목을 통과시킨다(permissive).
 *
 * Cycle C가 결정적 추출·대조 구현으로 이 빈을 대체(@Primary)하거나 교체한다. Cycle B에서는 흐름상 훅 위치만
 * 보존하고, 검증으로 인한 상태 변화를 만들지 않는다.
 */
@Component
class PermissiveGroundingValidator : GroundingValidator {

    override fun validate(sections: List<GeneratedSection>): List<SectionValidationResult> =
        sections.map { SectionValidationResult(definitionKey = it.definitionKey, valid = true) }
}
