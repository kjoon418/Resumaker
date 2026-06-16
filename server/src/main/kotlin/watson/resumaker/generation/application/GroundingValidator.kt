package watson.resumaker.generation.application

/**
 * 자동 검증 seam(구현 설계 §6, 도메인 이해 §421~429). **Cycle C 범위.**
 *
 * 이 사이클(B)에서는 인터페이스와 **permissive 기본 구현**([PermissiveGroundingValidator])만 둔다.
 * 생성 유스케이스 흐름(설계 §5의 4단계, 트랜잭션 밖)에서 이 훅의 호출 위치를 명확히 표시하되,
 * Cycle B는 항상 통과시킨다. Cycle C가 결정적 추출·대조와 자동 1회 재생성을 이 자리(같은 인터페이스)에서 삽입한다.
 *
 * 반쪽 구현 금지: 여기서 수치/고유명사 추출을 흉내 내지 않는다(Cycle C 책임). seam만 둔다.
 */
interface GroundingValidator {

    /**
     * 생성된 항목들을 검증해 통과/실패 표시를 돌려준다. Cycle B는 모두 통과시킨다.
     *
     * @param sections 포트가 돌려준 생성 항목들.
     * @return 각 항목의 검증 결과(Cycle B에서는 모두 valid=true).
     */
    fun validate(sections: List<GeneratedSection>): List<SectionValidationResult>
}

/**
 * 한 항목의 검증 결과. Cycle C가 valid=false인 항목을 VALIDATION_FAILED로 두고 자동 1회 재생성한다.
 */
data class SectionValidationResult(
    val definitionKey: String,
    val valid: Boolean,
)
