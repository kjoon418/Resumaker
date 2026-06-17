package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.SectionContent
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.infrastructure.ArtifactVersioningProperties
import watson.resumaker.generation.presentation.ArtifactResponse
import java.time.Clock
import java.time.Instant

/**
 * 항목 직접 편집 유스케이스(도메인 이해 §5 개선/직접 편집·§267·§271·§344, 수용 기준 10·19).
 *
 * 활성 버전의 한 생성 항목 텍스트를 사용자가 직접 수정한다. 직전 활성 버전을 복제한 위에 그 항목만 교체한
 * **새 버전**을 만들고 활성으로 전환한다(도메인 [watson.resumaker.artifact.domain.Artifact.editSection] —
 * 다른 항목은 그대로 복제, 이전 버전 보존). 편집된 항목은 사용자가 확정한 내용이므로 GENERATED로 둔다.
 *
 * **자동 검증 미적용(도메인 이해 §428):** 직접 편집 내용에는 신뢰성 자동 검증을 적용하지 않는다(검증은 AI 생성·
 * 재생성 결과에만). 따라서 외부 CLI/검증기/포트 호출이 전혀 없는 **순수 동기 영속 작업**이다. 외부 호출이 없으므로
 * 재생성과 달리 트랜잭션 분리·인메모리 락이 불필요하고, 단일 트랜잭션 안에서 적재→채택→영속→변환이면 충분하다
 * (Spring 트랜잭션 분리 가이드: 외부 호출 없으면 단일 tx). 지연 로딩 경계를 넘지 않도록 DTO 변환도 tx 내부에서 한다.
 *
 * **재생성 한도·비용(§397, 태스크 6) 무관:** 직접 편집은 재생성 한도·비용 차감과 무관하므로 [GenerationQuotaGuard]를
 * 호출하지 않는다(§397은 항목 재생성에 한정).
 *
 * **factGroundings 처리(도메인 이해 §382·§428):** [watson.resumaker.artifact.domain.Artifact.editSection]은
 * 편집 대상 항목의 factGroundings를 **빈 목록으로** 만든다. factGroundings는 AI가 산출한 "수치·고유명사 1:1 근거"
 * (§382)인데, 사용자가 직접 쓴 내용에는 해당 토큰별 근거가 더 이상 대응하지 않는다. 비워 "factGroundings = AI
 * 파생 근거" 불변식을 정직하게 유지한다. sourceExperienceIds(층위 1 출처)는 보존해 "근거 없이 만들어진 항목 0건"
 * 수용기준을 유지한다. 미변경 항목은 factGroundings를 포함해 그대로 복제된다(수용 기준 10).
 */
@Service
class SectionEditService(
    private val artifactRepository: ArtifactRepository,
    private val mapper: ArtifactReadServiceMapper,
    private val versioningProperties: ArtifactVersioningProperties,
    private val clock: Clock,
) {

    @Transactional
    fun editSectionContent(ownerId: UserId, command: EditSectionContentCommand): ArtifactResponse {
        val artifact = artifactRepository.findByIdAndOwnerId(command.artifactId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")
        // 활성 버전에 대상 항목이 없으면(미존재·타인) 404로 통일한다(존재 노출 최소화 — 재생성과 동형).
        // editSection도 항목 부재 시 DomainValidationException(→400)을 throw하므로, 여기서 먼저 짚어 404를 보장한다.
        artifact.activeVersion().sectionById(command.sectionId)
            ?: throw ResourceNotFoundException("수정할 항목을 찾을 수 없어요.")

        // content 길이 등 도메인 불변식은 SectionContent.of가 강제한다(초과 시 DomainValidationException → 400).
        val edited = SectionContent.of(command.content)
        val now = Instant.now(clock)
        // 도메인 editSection: 직전 활성 버전 복제 + 대상 항목 content 교체 + factGroundings 비움 + 새 활성 버전.
        // sourceExperienceIds는 보존, 미변경 항목은 factGroundings 포함 그대로 복제(수용 기준 10·19).
        artifact.editSection(command.sectionId, edited, now)
        // 보관 상한 정리(수용 기준 11): 새 버전 추가로 상한을 넘으면 같은 트랜잭션 안에서 가장 오래된 비활성
        // 버전부터 정리한다(orphanRemoval로 삭제 영속). 활성은 제외되어 불변식 유지. 사전 고지는 응답으로 내려준다.
        val pruned = artifact.pruneOldestIfExceeds(versioningProperties.versionRetentionLimit)
        val saved = artifactRepository.save(artifact)
        return mapper.toResponse(saved, prunedVersionCount = pruned.size)
    }
}
