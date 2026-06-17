package watson.resumaker.generation.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.ArtifactId
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.generation.presentation.ArtifactResponse
import watson.resumaker.generation.presentation.ArtifactVersionsResponse

/**
 * 산출물 열람 유스케이스(수용 기준 12). 활성 버전의 전체/항목 텍스트·상태·출처를 표시용으로 돌려준다.
 *
 * **소유 격리(구현 설계 §194·수용 기준 13):** findByIdAndOwnerId로 ownerId 조건을 강제하며, 타인 소유·미존재는
 * 동일하게 404로 매핑된다(존재 노출 최소화 — ResourceNotFoundException).
 *
 * **트랜잭션 내부 DTO 변환(구현 설계 §5·§221):** 활성 버전·항목은 지연 로딩 경계 안에 있으므로 변환을
 * readOnly 트랜잭션 내부에서 [ArtifactReadServiceMapper]로 수행한다.
 */
@Service
class ArtifactReadService(
    private val artifactRepository: ArtifactRepository,
    private val mapper: ArtifactReadServiceMapper,
) {

    @Transactional(readOnly = true)
    fun getArtifact(ownerId: UserId, id: ArtifactId): ArtifactResponse {
        val artifact = artifactRepository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")
        return mapper.toResponse(artifact)
    }

    /**
     * 한 산출물의 모든 버전을 비교용으로 돌려준다(수용 기준 11·12, 도메인 이해 §271~283·§363). 각 버전의 항목
     * (definitionKey·종류·내용·상태·출처)·활성 여부·생성시각을 생성 순서로 담는다. 클라이언트는 definitionKey로
     * 버전 간 '같은 항목'을 맞춰 비교한다(§363). 소유 격리·미존재는 [getArtifact]와 동일하게 404로 매핑된다.
     */
    @Transactional(readOnly = true)
    fun getVersions(ownerId: UserId, id: ArtifactId): ArtifactVersionsResponse {
        val artifact = artifactRepository.findByIdAndOwnerId(id, ownerId)
            ?: throw ResourceNotFoundException("요청하신 산출물을 찾을 수 없어요.")
        return mapper.toVersionsResponse(artifact)
    }
}
