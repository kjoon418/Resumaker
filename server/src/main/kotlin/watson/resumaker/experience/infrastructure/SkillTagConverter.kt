package watson.resumaker.experience.infrastructure

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import watson.resumaker.experience.domain.SkillTag

/**
 * SkillTag VO <-> String 컬럼 변환기.
 *
 * @Basic value class(UserId, ExperienceTitle 등)는 Hibernate 6이 내부 타입으로 자동 매핑하지만,
 * @ElementCollection 안의 value class 원소는 JdbcType을 추론하지 못한다
 * (JdbcTypeRecommendationException: Could not determine recommended JdbcType for ... SkillTag).
 * 따라서 element collection 원소에 한해 명시적 컨버터를 적용한다(@Convert).
 */
@Converter
class SkillTagConverter : AttributeConverter<SkillTag, String> {

    override fun convertToDatabaseColumn(attribute: SkillTag?): String? = attribute?.value

    override fun convertToEntityAttribute(dbData: String?): SkillTag? = dbData?.let { SkillTag(it) }
}
