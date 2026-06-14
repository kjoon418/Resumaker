package watson.resumaker.target.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.resumaker.account.domain.UserId
import watson.resumaker.target.domain.TargetBrief
import watson.resumaker.target.domain.TargetBriefId
import java.util.UUID

/**
 * 목표 정보 영속성. 모든 조회는 ownerId를 조건에 포함해 소유 격리를 강제한다(구현 설계 §4).
 *
 * 식별자 타입은 UUID다(TargetBriefId value class @Id → 내부 UUID 등록). 소유 격리 조회는
 * 파생 쿼리(findByIdAndOwnerId 등)로 VO를 그대로 받는다.
 */
interface TargetBriefRepository : JpaRepository<TargetBrief, UUID> {

    fun findByIdAndOwnerId(id: TargetBriefId, ownerId: UserId): TargetBrief?

    fun findAllByOwnerId(ownerId: UserId): List<TargetBrief>

    fun deleteByOwnerId(ownerId: UserId)
}
