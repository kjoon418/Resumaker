package watson.resumaker.experience.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    /**
     * 여러 경험을 단일 배치 쿼리로 적재한다(N+1 회피). 소유 격리를 위해 ownerId 조건을 함께 둔다.
     * 미존재·타소유 식별자는 결과에서 빠지므로, 호출자는 결과 수 == 요청 ids 수로 누락을 검출한다.
     *
     * ids는 원시 UUID로 받는다. value class([ExperienceRecordId])를 IN 컬렉션 파라미터로 바인딩하면
     * Hibernate가 원소를 UUID로 언래핑하지 못해 변환 예외가 난다(단일 @Id 언래핑과 달리 컬렉션은 미지원).
     * 따라서 명시 JPQL + UUID 파라미터로 두고, 식별자 언래핑은 호출자(서비스 경계)가 담당한다.
     */
    @Query("select e from ExperienceRecord e where e.id in :ids and e.ownerId = :ownerId")
    fun findAllByIdInAndOwnerId(
        @Param("ids") ids: Collection<UUID>,
        @Param("ownerId") ownerId: UserId,
    ): List<ExperienceRecord>

    fun findAllByOwnerId(ownerId: UserId): List<ExperienceRecord>

    fun deleteByOwnerId(ownerId: UserId)
}
