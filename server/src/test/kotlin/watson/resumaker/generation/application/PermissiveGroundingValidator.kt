package watson.resumaker.generation.application

/**
 * 테스트 전용 통과 검증기. 자동 검증과 무관한 흐름(부분 실패·정합화 등)을 검증하는 테스트에서,
 * 검증을 항상 통과시켜 흐름을 단순화하기 위한 더블이다(프로덕션은 [DeterministicGroundingValidator]).
 */
class PermissiveGroundingValidator : GroundingValidator {

    override fun validate(section: GeneratedSection, sources: List<ExperienceSnapshot>): SectionValidationResult =
        SectionValidationResult(definitionKey = section.definitionKey, valid = true)
}
