package watson.resumaker.model.dto

import kotlinx.serialization.Serializable
import watson.resumaker.model.type.SectionCharacter

/**
 * 이력서 양식 DTO(FU-A). 서버 `template.presentation.TemplateDtos`와 구조적으로 대응하되,
 * 의도적 비대칭이 있다:
 * - 서버 `CreateTemplateRequest.sections`는 `@NotEmpty` 검증이 붙은 non-null List이고
 *   `SectionRequest.name`은 `@NotBlank` 검증이 붙지만, 클라이언트 DTO는 Bean Validation 없이
 *   Kotlin 타입 시스템(non-null)으로만 보장한다 — 유효성 검사는 ViewModel에서 수행한다.
 * - `TemplateResponse.sections`는 서버 응답의 빈 목록도 수용할 수 있도록 기본값 `emptyList()`를 둔다.
 */
@Serializable
data class CreateTemplateRequest(
    val name: String,
    val sections: List<SectionRequest>,
)

@Serializable
data class UpdateTemplateRequest(
    val name: String,
    val sections: List<SectionRequest>,
)

@Serializable
data class SectionRequest(
    val name: String,
    val character: SectionCharacter,
    val required: Boolean = false,
)

@Serializable
data class TemplateResponse(
    val id: String,
    val name: String,
    val sections: List<SectionResponse> = emptyList(),
)

@Serializable
data class SectionResponse(
    val name: String,
    val character: SectionCharacter,
    val required: Boolean = false,
)
