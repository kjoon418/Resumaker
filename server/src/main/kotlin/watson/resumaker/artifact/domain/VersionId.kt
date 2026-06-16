package watson.resumaker.artifact.domain

import java.util.UUID

/**
 * 타입 안전 버전 식별자(구현 설계 §3.1).
 * Artifact 애그리거트 내부 식별자이며, 도메인이 생성 시점에 UUID로 발급한다.
 */
@JvmInline
value class VersionId(val value: UUID)
