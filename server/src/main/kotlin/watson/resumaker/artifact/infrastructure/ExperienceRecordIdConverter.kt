package watson.resumaker.artifact.infrastructure

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

/**
 * ExperienceRecordId VO <-> String 컬럼 변환기.
 *
 * ArtifactSection.sourceExperienceIds는 @ElementCollection으로 저장한다. @ElementCollection 안의
 * value class 원소는 Hibernate가 JdbcType을 추론하지 못하므로(JdbcTypeRecommendationException,
 * SkillTagConverter 주석 참고) 명시적 컨버터로 String(UUID 텍스트)으로 매핑한다.
 *
 * **스냅샷 격리(구현 설계 §164·§195):** 경험 식별자는 String 컬럼으로만 저장하고 experience_records에
 * FK·cascade를 걸지 않는다. 따라서 삭제된 경험을 가리켜도 산출물 버전은 무너지지 않는다(정상).
 */
@Converter
class ExperienceRecordIdConverter : AttributeConverter<ExperienceRecordId, String> {

    override fun convertToDatabaseColumn(attribute: ExperienceRecordId?): String? =
        attribute?.value?.toString()

    override fun convertToEntityAttribute(dbData: String?): ExperienceRecordId? =
        dbData?.let { ExperienceRecordId(UUID.fromString(it)) }
}
