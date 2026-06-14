package watson.resumaker.model.type

import kotlinx.serialization.Serializable

/**
 * 경험 유형(백엔드 enum과 1:1, 브리프 §DTO).
 * 직렬화 이름은 서버 enum 상수명과 동일해야 한다(JSON 정합).
 */
@Serializable
enum class ExperienceType {
    PROJECT,
    JOB,
    EXTRACURRICULAR,
    AWARD,
    LEARNING,
}
