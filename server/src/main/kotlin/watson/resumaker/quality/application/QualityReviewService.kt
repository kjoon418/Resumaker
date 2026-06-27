package watson.resumaker.quality.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactKind
import watson.resumaker.artifact.domain.ArtifactSection
import watson.resumaker.artifact.domain.SectionId
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.generation.application.FactTokenExtractor
import watson.resumaker.quality.domain.Finding
import watson.resumaker.quality.domain.QualityCriterion
import watson.resumaker.quality.domain.QualityReport
import watson.resumaker.quality.domain.ReviewedSection
import watson.resumaker.quality.domain.SuggestionGuide
import watson.resumaker.quality.domain.TreatmentKind
import watson.resumaker.quality.infrastructure.QualityCriteriaDictionary

/**
 * 품질 점검(진단) 유스케이스(품질 개선 기획 §3.4 2단계 중 1단계, §3.6, 수용 기준 QC1·QC2·QC8·QC10·QC11·QC12).
 *
 * 산출물의 **활성 버전 전체**를 개선 기준에 비추어 결정적으로 진단해 [QualityReport]를 낸다. 진단은 **무비용·동기**다
 * (오너 확정 §5.1-3: 진단 무차감). 외부 LLM을 호출하지 않으므로 readOnly 트랜잭션 안에서 모두 끝난다.
 *
 * **개선 대상 가드(QC10 — 오너 확정 §5.1-2):** 이력서 한정. 포트폴리오 산출물에는 품질 개선 진입점을 노출하지
 * 않으므로, 서버단에서도 RESUME가 아니면 거절한다(DomainValidationException → 400).
 *
 * **소유 격리(QC8):** findByIdAndOwnerId로 ownerId 조건을 강제하며, 타인 소유·미존재는 동일하게 404로 매핑한다.
 *
 * **처치 종류 분기(QC11 — §130):** 같은 소견이라도 경험 기록에 근거가 있으면 AUTO_REWRITE, 없으면 SUGGESTION이다.
 * 모호 수치(I4)는 출처 경험에 실측값(수치 토큰)이 있으면 자동 적용(객관화), 없으면 보강 안내로 돌린다(AP5 핵심).
 * 표현만 다듬는 소견(약한 동사·버즈워드·수동태·길이·중복)은 사실 불변이라 항상 AUTO_REWRITE다.
 */
@Service
class QualityReviewService(
    private val artifactRepository: ArtifactRepository,
    private val experienceRepository: ExperienceRecordRepository,
    private val checks: QualityCriteriaDictionary,
    private val factTokenExtractor: FactTokenExtractor,
) {

    @Transactional(readOnly = true)
    fun review(ownerId: UserId, artifactId: java.util.UUID): QualityReport {
        val artifact = artifactRepository.findByIdAndOwnerId(
            watson.resumaker.artifact.domain.ArtifactId(artifactId),
            ownerId,
        ) ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")

        // QC10: 포트폴리오는 MVP 자동 개선 대상이 아니다(오너 확정 §5.1-2).
        if (artifact.kind != ArtifactKind.RESUME) {
            throw DomainValidationException("품질 점검은 이력서 산출물에서만 사용할 수 있어요.")
        }

        val active = artifact.activeVersion()
        val sections = active.sections
        // 출처 경험을 소유 격리로 한 번에 적재한다(처치 분기의 근거 유무 판정용 — 삭제된 경험은 빠질 수 있음).
        val experiencesById = loadExperiences(ownerId, sections)

        val findings = mutableListOf<Finding>()
        sections.forEach { section ->
            findings += reviewSection(section, experiencesById)
        }
        // 항목을 가로지르는 중복(C3)은 항목 쌍 단위로 검출한다.
        findings += findDuplications(sections)

        // 소견이 달린 항목만, 활성 버전 순서대로 표시 맥락(이름·내용)을 담는다(클라이언트의 항목별 묶음·정박용).
        val sectionIdsWithFindings = findings.map { it.sectionId }.toSet()
        val reviewedSections = sections
            .filter { it.id in sectionIdsWithFindings }
            .map { ReviewedSection(it.id, it.definitionKey, it.content.value) }

        return QualityReport(
            artifactId = artifact.id.value,
            versionId = active.id.value,
            findings = findings,
            sections = reviewedSections,
        )
    }

    // ----- 항목 단위 검사 -----

    private fun reviewSection(
        section: ArtifactSection,
        experiencesById: Map<ExperienceRecordId, ExperienceRecord>,
    ): List<Finding> {
        val content = section.content.value
        val findings = mutableListOf<Finding>()

        // St2 빈 항목(자동) — 비어 있으면 채울 근거가 없으므로 보강 안내(빈자리는 채우지 않고 알린다 — §80).
        if (checks.isEmptyContent(content)) {
            findings += finding(
                section, QualityCriterion.EMPTY_SECTION, TreatmentKind.SUGGESTION,
                suggestionGuide = guideFor(section, experiencesById, "이 항목을 채울 경험 내용을 보강해 주세요."),
            )
            return findings // 빈 항목은 다른 텍스트 검사가 의미 없다.
        }

        // C1 분량(자동) — 표현만 압축하므로 자동 적용.
        if (checks.exceedsLength(content)) {
            findings += finding(section, QualityCriterion.LENGTH, TreatmentKind.AUTO_REWRITE, evidenceText = null)
        }
        // I1 약한 동사(반자동) — 행동 동사로 교체(사실 불변 → 자동 적용).
        checks.findWeakVerb(content)?.let {
            findings += finding(section, QualityCriterion.STRONG_VERB, TreatmentKind.AUTO_REWRITE, evidenceText = it)
        }
        // I2 수동태(반자동) — 능동태 전환(자동 적용).
        checks.findPassiveVoice(content)?.let {
            findings += finding(section, QualityCriterion.ACTIVE_VOICE, TreatmentKind.AUTO_REWRITE, evidenceText = it)
        }
        // C2 버즈워드(반자동) — 구체 표현 치환(자동 적용).
        checks.findBuzzword(content)?.let {
            findings += finding(section, QualityCriterion.BUZZWORD, TreatmentKind.AUTO_REWRITE, evidenceText = it)
        }
        // I4 모호 수치·규모어(반자동) — QC11 분기: 출처 경험에 실측값(수치)이 있으면 자동 적용, 없으면 보강 안내.
        checks.findVagueMetric(content)?.let { term ->
            if (hasNumericEvidence(section, experiencesById)) {
                findings += finding(section, QualityCriterion.VAGUE_METRIC, TreatmentKind.AUTO_REWRITE, evidenceText = term)
            } else {
                // AI-12: 역할/규모 형용사("중요한 역할"·"복잡한")는 수치 객관화가 아니라 근거(행동·기술)를 적도록 안내한다.
                val message = if (checks.isVagueRoleAdjective(term)) {
                    "‘$term’ 같은 표현이 모호해요. 어떤 행동·기술로 그렇게 판단했는지 경험 기록에 적어 주세요."
                } else {
                    "이 수치를 객관화하려면 경험 기록에 구체적인 값(예: 100→200명, 300ms→210ms)을 적어 주세요."
                }
                findings += finding(
                    section, QualityCriterion.VAGUE_METRIC, TreatmentKind.SUGGESTION, evidenceText = term,
                    suggestionGuide = guideFor(section, experiencesById, message),
                )
            }
        }
        // K1 끝맺음 단조(AI-09) — 표현 다듬기지만 유료 처치 남발을 피해 개선 제안(SUGGESTION)으로만 안내.
        checks.findMonotonousEnding(content)?.let { ending ->
            findings += finding(
                section, QualityCriterion.MONOTONOUS_ENDING, TreatmentKind.SUGGESTION, evidenceText = ending,
                suggestionGuide = SuggestionGuide(message = "‘…$ending’ 같은 끝맺음이 반복돼요. 문장 끝을 다양하게 바꾸면 읽기 좋아요."),
            )
        }
        // K2 표기 변형(AI-09) — 같은 용어의 표기를 섞어 쓴 경우. 자동 통일은 의도와 어긋날 수 있어 개선 제안으로 둔다.
        checks.findNotationVariant(content)?.let { variants ->
            findings += finding(
                section, QualityCriterion.NOTATION_VARIANT, TreatmentKind.SUGGESTION, evidenceText = variants,
                suggestionGuide = SuggestionGuide(message = "‘$variants’처럼 같은 용어의 표기가 섞여 있어요. 하나로 통일해 주세요."),
            )
        }
        // I3 성과 미반영(AI-09) — 출처 경험에 측정 가능한 성과(result의 수치)가 있는데 항목에 반영되지 않았으면 보강 안내.
        findResultNotReflected(section, content, experiencesById)?.let { targetId ->
            findings += finding(
                section, QualityCriterion.RESULT_NOT_REFLECTED, TreatmentKind.SUGGESTION,
                suggestionGuide = SuggestionGuide(
                    message = "이 경험에는 성과(수치)가 있는데 항목에 드러나지 않았어요. 성과를 반영하면 설득력이 높아져요.",
                    targetExperienceId = targetId,
                ),
            )
        }
        return findings
    }

    /**
     * I3(AI-09): 항목의 출처 경험 중 **result에 수치가 있는데** 항목 본문이 그 수치를 하나도 담지 않은 첫 경험 id를
     * 돌려준다(없으면 null). 측정 가능한 성과가 이력서에서 누락된 결정적 신호다.
     */
    private fun findResultNotReflected(
        section: ArtifactSection,
        content: String,
        experiencesById: Map<ExperienceRecordId, ExperienceRecord>,
    ): ExperienceRecordId? {
        val sectionNumbers = factTokenExtractor.extractNumericValues(content)
        return section.sourceExperienceIds.firstOrNull { id ->
            val experience = experiencesById[id] ?: return@firstOrNull false
            val result = experience.detail.result
            if (result.isNullOrBlank()) return@firstOrNull false
            val resultNumbers = factTokenExtractor.extractNumericValues(result)
            resultNumbers.isNotEmpty() && resultNumbers.none { it in sectionNumbers }
        }
    }

    /** 항목 간 중복(C3): 모든 항목 쌍을 비교해 유사하면 **뒤 항목**에 소견을 단다(앞 항목으로 통합 — 자동 적용). */
    private fun findDuplications(sections: List<ArtifactSection>): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (i in sections.indices) {
            for (j in i + 1 until sections.size) {
                val a = sections[i]
                val b = sections[j]
                if (a.content.value.isNotBlank() && b.content.value.isNotBlank() &&
                    checks.isDuplicate(a.content.value, b.content.value)
                ) {
                    findings += finding(
                        b, QualityCriterion.DUPLICATION, TreatmentKind.AUTO_REWRITE,
                        evidenceText = a.definitionKey,
                    )
                }
            }
        }
        return findings
    }

    // ----- 처치 분기 보조 -----

    /**
     * 항목의 출처 경험 본문에 **객관화에 쓸 만한** 정량 수치가 하나라도 있으면 true(I4 자동 적용 가능 조건 — §203).
     *
     * AI-10: 연도(19xx/20xx)·순수 인원(단위 "명")처럼 모호 수치 객관화와 **무관한 숫자**는 근거에서 제외한다.
     * 이런 숫자만 있는 경험을 근거로 AUTO_REWRITE(유료)로 오라우팅하면, 막상 객관화할 값이 없어 비용만 낭비된다.
     * 제외 후 남는 수치가 없으면 SUGGESTION(무비용 보강 안내)으로 돌린다(비용 가드레일상 안전한 방향).
     */
    private fun hasNumericEvidence(
        section: ArtifactSection,
        experiencesById: Map<ExperienceRecordId, ExperienceRecord>,
    ): Boolean =
        section.sourceExperienceIds.any { id ->
            val experience = experiencesById[id] ?: return@any false
            factTokenExtractor.extractNumericFacts(corpusOf(experience)).any { it.isObjectifiable() }
        }

    /** 객관화에 쓸 만한 수치인가(AI-10): 연도·순수 인원은 제외. */
    private fun watson.resumaker.generation.application.NumericFact.isObjectifiable(): Boolean {
        if (unit == "명") return false // 순수 인원 수는 모호 수치 객관화와 무관.
        if (unit == "년") return false // "2021년" 류 연도.
        // 단위 없는 4자리 연도(1900~2099 정수)도 제외.
        if (unit == null && isYearLike(value)) return false
        return true
    }

    private fun isYearLike(value: java.math.BigDecimal): Boolean {
        if (value.stripTrailingZeros().scale() > 0) return false // 정수만 연도 후보.
        return value >= YEAR_MIN && value <= YEAR_MAX
    }

    private fun guideFor(
        section: ArtifactSection,
        experiencesById: Map<ExperienceRecordId, ExperienceRecord>,
        message: String,
    ): SuggestionGuide {
        // 보강 대상은 항목의 첫 출처 경험으로 안내한다(프론트가 "그 경험 보강하러 가기"로 연결 — §243).
        val target = section.sourceExperienceIds.firstOrNull { experiencesById.containsKey(it) }
        return SuggestionGuide(message = message, targetExperienceId = target)
    }

    private fun finding(
        section: ArtifactSection,
        criterion: QualityCriterion,
        treatmentKind: TreatmentKind,
        evidenceText: String? = null,
        suggestionGuide: SuggestionGuide? = null,
    ): Finding = Finding(
        // 휘발 진단이므로 findingId는 (항목+기준) 결정적 파생값으로 둔다(같은 진단 입력 → 같은 id, QC1 결정성).
        findingId = "${section.id.value}:${criterion.criterionId}",
        sectionId = section.id,
        definitionKey = section.definitionKey,
        criterion = criterion,
        treatmentKind = treatmentKind,
        evidenceText = evidenceText,
        suggestionGuide = suggestionGuide,
    )

    private fun loadExperiences(
        ownerId: UserId,
        sections: List<ArtifactSection>,
    ): Map<ExperienceRecordId, ExperienceRecord> {
        val ids = sections.flatMap { it.sourceExperienceIds }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return experienceRepository.findAllByIdInAndOwnerId(ids.map { it.value }, ownerId)
            .associateBy { it.id }
    }

    private fun corpusOf(experience: ExperienceRecord): String = buildString {
        append(experience.title.value).append('\n')
        append(experience.body.value).append('\n')
        experience.detail.situation?.let { append(it).append('\n') }
        experience.detail.action?.let { append(it).append('\n') }
        experience.detail.result?.let { append(it).append('\n') }
        if (experience.detail.skillTags.isNotEmpty()) append(experience.detail.skillTags.joinToString(" ") { it.value })
    }

    companion object {
        private val YEAR_MIN = java.math.BigDecimal(1900)
        private val YEAR_MAX = java.math.BigDecimal(2099)
    }
}
