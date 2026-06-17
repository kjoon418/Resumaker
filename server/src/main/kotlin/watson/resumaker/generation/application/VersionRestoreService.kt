package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.domain.VersionId
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.presentation.ArtifactResponse

/**
 * 버전 복원 유스케이스(도메인 이해 §277·§283, 구현 설계 §3.6 "복원 = 활성 전환", 수용 기준 11·12).
 *
 * 사용자가 고른 이전 버전을 **활성으로 되돌린다**. 도메인 [watson.resumaker.artifact.domain.Artifact.restoreVersion]은
 * 기존 버전의 activeVersionId만 재지정하므로(새 버전 미생성), 이전 버전이 그대로 보존되고 버전 생성 순서·보관 상한에
 * 영향이 없다. 따라서 보관 상한 정리도 불필요하다(버전 수 불변).
 *
 * **자동 검증·AI 미적용:** 복원은 과거 스냅샷을 있는 그대로 다시 보는 행위라 외부 CLI/검증기 호출이 전혀 없는
 * **순수 동기 영속 작업**이다. 단일 트랜잭션 안에서 적재→활성 전환→영속→변환이면 충분하다(외부 호출 없으면 단일 tx —
 * Spring 트랜잭션 분리 가이드). 지연 로딩 경계를 넘지 않도록 DTO 변환도 tx 내부에서 한다.
 *
 * **소유 격리·미존재(구현 설계 §194, 수용 기준 13):** 산출물은 findByIdAndOwnerId로 ownerId를 강제해 타인 소유·
 * 미존재를 404로 통일한다. 대상 버전이 그 산출물에 없으면(미존재·타인 버전) 역시 404로 통일한다(존재 노출 최소화).
 * restoreVersion도 버전 부재 시 DomainValidationException(→400)을 throw하므로, 여기서 먼저 짚어 404를 보장한다.
 */
@Service
class VersionRestoreService(
    private val artifactRepository: ArtifactRepository,
    private val mapper: ArtifactReadServiceMapper,
) {

    @Transactional
    fun restoreVersion(ownerId: UserId, command: RestoreVersionCommand): ArtifactResponse {
        val artifact = artifactRepository.findByIdAndOwnerId(command.artifactId, ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")
        if (artifact.versions.none { it.id == command.versionId }) {
            throw ResourceNotFoundException("되돌릴 버전을 찾을 수 없어요.")
        }
        artifact.restoreVersion(command.versionId)
        val saved = artifactRepository.save(artifact)
        return mapper.toResponse(saved)
    }
}

/**
 * 버전 복원 유스케이스 입력 커맨드(도메인 이해 §277·§283). 산출물·버전 식별자는 경로 변수에서 온다.
 *
 * @param artifactId 복원 대상 산출물(소유 격리).
 * @param versionId  활성으로 되돌릴 버전.
 */
data class RestoreVersionCommand(
    val artifactId: ArtifactId,
    val versionId: VersionId,
)
