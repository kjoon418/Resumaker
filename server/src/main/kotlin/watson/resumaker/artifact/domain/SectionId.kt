package watson.resumaker.artifact.domain

import java.util.UUID

/**
 * 타입 안전 생성 항목 식별자(구현 설계 §3.1).
 * Version 내부의 ArtifactSection을 가리키며, 도메인이 생성 시점에 UUID로 발급한다.
 */
@JvmInline
value class SectionId(val value: UUID)
