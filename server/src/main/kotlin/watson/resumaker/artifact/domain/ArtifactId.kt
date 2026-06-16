package watson.resumaker.artifact.domain

import java.util.UUID

/**
 * 타입 안전 산출물 식별자(구현 설계 §3.1).
 *
 * value class로 둔다. Hibernate 6이 inline class를 내부 타입(UUID)으로 네이티브 매핑하며,
 * 식별자 타입은 UUID로 등록된다(레포지토리는 JpaRepository<_, UUID>).
 */
@JvmInline
value class ArtifactId(val value: UUID)
