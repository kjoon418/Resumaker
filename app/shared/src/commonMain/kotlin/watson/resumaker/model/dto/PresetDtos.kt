package watson.resumaker.model.dto

import kotlinx.serialization.Serializable
import watson.resumaker.model.type.SectionCharacter

/**
 * 프리셋 양식 DTO(FU-B). 서버 `template.presentation.TemplatePresetResponse`와 구조적으로 대응한다.
 * 클라이언트는 Bean Validation 없이 Kotlin 타입 시스템으로 non-null을 보장한다.
 */
@Serializable
data class TemplatePresetResponse(
    val key: String,
    val name: String,
    val sections: List<SectionResponse> = emptyList(),
)

/**
 * 회사 양식 붙여넣기 해석 요청 DTO(FU-C). 서버 `InterpretRequest`와 1:1.
 */
@Serializable
data class InterpretRequest(
    val text: String,
)

/**
 * 해석 결과 응답 DTO(FU-C). 서버 `InterpretResponse`와 1:1.
 * - status = "interpreted": [sections]에 후보 섹션 목록.
 * - status = "unavailable": 해석 불가, 프론트는 폴백 UI를 보여준다.
 */
@Serializable
data class InterpretResponse(
    val status: String,
    val sections: List<SectionResponse> = emptyList(),
) {
    val isInterpreted: Boolean get() = status == STATUS_INTERPRETED

    companion object {
        const val STATUS_INTERPRETED = "interpreted"
        const val STATUS_UNAVAILABLE = "unavailable"
    }
}
