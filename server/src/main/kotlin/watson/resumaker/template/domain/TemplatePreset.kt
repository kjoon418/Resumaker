package watson.resumaker.template.domain

/**
 * 서비스가 제공하는 표준 이력서 양식 프리셋(도메인 이해 §2.5 "프리셋 선택").
 *
 * 프리셋은 사용자 소유가 아니라 서비스가 제공한다. 사용자가 프리셋을 고르면 이름+섹션을
 * 편집 화면에 미리 채운 후 자신만의 양식으로 저장한다.
 *
 * @param key 프리셋 식별자(URL-safe 영문 slug).
 * @param name 사용자에게 노출되는 프리셋 이름.
 * @param sections 섹션 정의 목록(순서 있음).
 */
data class TemplatePreset(
    val key: String,
    val name: String,
    val sections: List<SectionDefinition>,
)
