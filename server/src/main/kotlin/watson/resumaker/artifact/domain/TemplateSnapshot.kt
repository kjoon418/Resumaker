package watson.resumaker.artifact.domain

import watson.resumaker.common.domain.DomainValidationException

/**
 * 산출물이 소유하는 양식의 **불변 스냅샷**(도메인 이해 §360, 수용 기준 22).
 *
 * 이력서 산출물은 생성 시점 양식의 섹션 정의 집합을 통째로 복제한 스냅샷 하나를 가지며,
 * 그 산출물의 **모든 버전이 이 스냅샷을 공유**한다. 포트폴리오는 스냅샷이 없다(Artifact.templateSnapshot == null).
 *
 * 순수 도메인 VO다(JPA 어노테이션 없음). 영속은 Artifact 엔티티의 @ElementCollection<SnapshotSection>으로
 * 펼쳐 저장하고, 복원 시 이 VO로 다시 감싼다. 불변(생성 후 변경 불가)이므로 모든 버전이 안전하게 공유한다.
 *
 * 불변식:
 * - 섹션 최소 1개(빈 스냅샷 불성립 — 원본 양식 불변식과 동형).
 * - definitionKey는 스냅샷 내에서 유일(버전 간 항목 대응 키이므로 중복 불가).
 */
class TemplateSnapshot private constructor(
    val sections: List<SnapshotSection>,
) {

    /** 주어진 키의 섹션 정의를 찾는다(버전 간 항목 대응에 사용). */
    fun sectionByKey(definitionKey: String): SnapshotSection? =
        sections.firstOrNull { it.definitionKey == definitionKey }

    companion object {
        fun of(sections: List<SnapshotSection>): TemplateSnapshot {
            if (sections.isEmpty()) {
                throw DomainValidationException("양식 스냅샷에는 섹션이 적어도 하나 필요해요.")
            }
            val keys = sections.map { it.definitionKey }
            if (keys.toSet().size != keys.size) {
                throw DomainValidationException("양식 스냅샷의 섹션 정의 키는 서로 달라야 해요.")
            }
            return TemplateSnapshot(sections.toList())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TemplateSnapshot) return false
        return sections == other.sections
    }

    override fun hashCode(): Int = sections.hashCode()
}
