package watson.resumaker.experience.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.resumaker.account.domain.UserId
import watson.resumaker.experience.domain.ExperienceRecord
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

/**
 * 경험 기록 영속성. 모든 조회는 ownerId를 조건에 포함해 소유 격리를 강제한다(구현 설계 §4).
 *
 * 식별자 타입은 UUID다(ExperienceRecordId value class @Id → 내부 UUID 등록). 소유 격리 조회는
 * 파생 쿼리(findByIdAndOwnerId 등)로 VO를 그대로 받는다.
 */
interface ExperienceRecordRepository : JpaRepository<ExperienceRecord, UUID> {

    fun findByIdAndOwnerId(id: ExperienceRecordId, ownerId: UserId): ExperienceRecord?

    fun findAllByOwnerId(ownerId: UserId): List<ExperienceRecord>

    fun deleteByOwnerId(ownerId: UserId)
}
