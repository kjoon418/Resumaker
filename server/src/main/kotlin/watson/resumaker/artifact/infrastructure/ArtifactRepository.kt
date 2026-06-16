package watson.resumaker.artifact.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.resumaker.account.domain.UserId
import watson.resumaker.artifact.domain.Artifact
import watson.resumaker.artifact.domain.ArtifactId
import java.util.UUID

/**
 * 산출물 영속성. 모든 조회는 ownerId를 조건에 포함해 소유 격리를 강제한다(구현 설계 §4·§194).
 *
 * 식별자 타입은 UUID다(ArtifactId value class @Id → 내부 UUID 등록). 소유 격리 조회는 파생 쿼리
 * (findByIdAndOwnerId 등)로 VO를 그대로 받는다. deleteByOwnerId는 계정 삭제 시 귀속 데이터 정리용이다.
 *
 * **스냅샷 격리(구현 설계 §164·§195):** Version/Section/FactGrounding은 애그리거트 내부 cascade로 함께
 * 영속되며, 경험 식별자(sourceExperienceIds/FactGrounding.sourceExperienceId)는 String 컬럼만 두고
 * experience_records에 FK를 걸지 않는다(삭제된 경험을 가리켜도 정상).
 */
interface ArtifactRepository : JpaRepository<Artifact, UUID> {

    fun findByIdAndOwnerId(id: ArtifactId, ownerId: UserId): Artifact?

    fun findAllByOwnerId(ownerId: UserId): List<Artifact>

    fun deleteByOwnerId(ownerId: UserId)
}
