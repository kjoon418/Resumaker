package watson.resumaker.generation.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 산출물 버전 정책 외부 설정(도메인 이해 §398 "버전 보관 개수 상한", 수용 기준 11, 구현 설계 §7
 * "구체 수치는 @ConfigurationProperties로 외부화").
 *
 * - [versionRetentionLimit]: 산출물당 보관 버전 수 상한. 새 버전을 추가하는 경로(재생성·직접 편집)에서
 *   영속과 같은 트랜잭션 안에 이 상한으로 정리한다([watson.resumaker.artifact.domain.Artifact.pruneOldestIfExceeds]).
 *   기본 10은 "비교·복원에 충분하되 무한 누적은 막는" MVP 적정선이다(§401: 수치는 운영 정책으로 조정).
 *   활성 버전은 정리 대상에서 제외되므로 상한이 1이어도 불변식(항상 활성 존재)은 유지된다(§135).
 */
@ConfigurationProperties(prefix = "resumaker.artifact")
data class ArtifactVersioningProperties(
    val versionRetentionLimit: Int = 10,
)
