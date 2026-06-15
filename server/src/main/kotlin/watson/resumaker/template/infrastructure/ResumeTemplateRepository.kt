package watson.resumaker.template.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.resumaker.account.domain.UserId
import watson.resumaker.template.domain.ResumeTemplate
import watson.resumaker.template.domain.ResumeTemplateId
import java.util.UUID

/**
 * 이력서 양식 영속성. 모든 조회는 ownerId를 조건에 포함해 소유 격리를 강제한다(구현 설계 §4).
 *
 * 식별자 타입은 UUID다(ResumeTemplateId value class @Id → 내부 UUID 등록). 소유 격리 조회는
 * 파생 쿼리(findByIdAndOwnerId 등)로 VO를 그대로 받는다.
 */
interface ResumeTemplateRepository : JpaRepository<ResumeTemplate, UUID> {

    fun findByIdAndOwnerId(id: ResumeTemplateId, ownerId: UserId): ResumeTemplate?

    fun findAllByOwnerId(ownerId: UserId): List<ResumeTemplate>

    fun deleteByOwnerId(ownerId: UserId)
}
