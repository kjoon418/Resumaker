package watson.resumaker.artifact.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.experience.domain.ExperienceRecordId

/**
 * 고정밀 사실 근거(층위 2 — 도메인 이해 §381). 산출물에 등장한 수치/고유명사 각각이
 * 그것을 뒷받침하는 경험 기록과 그 안의 근거 문자열에 연결된다(사실 토큰 1 — 1 근거).
 *
 * Artifact 애그리거트 내부 엔티티이며 ArtifactSection의 자식으로 cascade ALL+orphanRemoval된다.
 * 순서 보존(@OrderColumn)을 위해 surrogate Long @Id를 둔다(값 식별자 SectionId/VersionId와 달리
 * FactGrounding은 외부에서 식별·참조되지 않는 내부 라인이므로 DB 생성 키로 충분).
 *
 * **JPA 매핑 정책:** token은 FactToken value class를 직접 컬럼에 두면 추론 함정 위험이 있어
 * 원시 String 컬럼으로 저장하고 경계(create/token())에서 VO로 감싼다. 게터는 신뢰 복원 경로
 * (FactToken.ofTrusted)로 재검증 없이 래핑하므로 읽기에서 예외가 튀지 않는다.
 *
 * **양방향 매핑(구현 설계 best practice):** FK는 자식이 소유한다(@ManyToOne optional=false). 부모
 * ArtifactSection은 mappedBy로 참조하며, 부모 설정 시 자식의 section 참조를 주입해 일관성을 유지한다.
 *
 * **스냅샷 격리:** sourceExperienceId는 식별자 String 컬럼만 둔다(experience_records FK 금지).
 */
@Entity
@Table(name = "fact_groundings")
class FactGrounding private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long?,
    @Column(name = "token", nullable = false, length = FactToken.MAX_LENGTH)
    val tokenValue: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    val kind: FactKind,
    @Column(name = "source_experience_id", nullable = false)
    val sourceExperienceId: ExperienceRecordId,
    @Column(name = "evidence_text", nullable = false, length = MAX_EVIDENCE_LENGTH)
    val evidenceText: String,
) {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_section_id", nullable = false)
    private var section: ArtifactSection? = null

    /** 부모 ArtifactSection이 자식 목록을 구성할 때 역참조를 주입한다(양방향 일관성). */
    internal fun assignSection(owner: ArtifactSection) {
        this.section = owner
    }

    /** 토큰을 도메인 VO로 노출한다(검증된 형태, 재검증 없이 신뢰 복원). */
    val token: FactToken get() = FactToken.ofTrusted(tokenValue)

    companion object {
        const val MAX_EVIDENCE_LENGTH = 2000

        fun create(
            token: FactToken,
            kind: FactKind,
            sourceExperienceId: ExperienceRecordId,
            evidenceText: String,
        ): FactGrounding {
            requireEvidenceWithinLimit(evidenceText)
            return FactGrounding(
                id = null,
                tokenValue = token.value,
                kind = kind,
                sourceExperienceId = sourceExperienceId,
                evidenceText = evidenceText,
            )
        }

        /** 직전 버전의 근거를 새 버전으로 그대로 복제한다(미변경 항목 복제 시 사용). */
        fun copyOf(source: FactGrounding): FactGrounding = FactGrounding(
            id = null,
            tokenValue = source.tokenValue,
            kind = source.kind,
            sourceExperienceId = source.sourceExperienceId,
            evidenceText = source.evidenceText,
        )

        private fun requireEvidenceWithinLimit(evidenceText: String) {
            if (evidenceText.length > MAX_EVIDENCE_LENGTH) {
                throw DomainValidationException("사실 근거 문장은 ${MAX_EVIDENCE_LENGTH}자 이내여야 해요.")
            }
        }
    }
}
