package watson.resumaker.account.domain

import java.util.UUID

/**
 * 타입 안전 사용자 식별자(구현 설계 §3.1).
 * 다른 애그리거트는 이 식별자로만 사용자를 참조한다(소유 격리).
 *
 * value class로 둔다. Hibernate 6은 inline class를 내부 타입(UUID)으로 네이티브 매핑하므로
 * @Id·@Basic(ownerId) 모두 컨버터 없이 동작한다. 식별자 타입은 UUID로 등록되므로
 * 레포지토리는 JpaRepository<_, UUID>로 두고 findById/deleteById 등은 id.value(UUID)로 호출한다.
 */
@JvmInline
value class UserId(val value: UUID)
