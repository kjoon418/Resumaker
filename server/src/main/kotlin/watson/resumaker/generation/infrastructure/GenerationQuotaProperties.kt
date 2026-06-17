package watson.resumaker.generation.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 비용 가드레일 수치 외부 설정(도메인 이해 §394~401·열린질문#1, 구현 설계 §7
 * "구체 수치는 @ConfigurationProperties로 외부화"). [ArtifactVersioningProperties]와 동일한 패턴.
 *
 * 카운트 대상·차감 시점·리셋 경계(사용자 시간대 달력일)는 도메인 규칙으로 고정이고, 여기서는 **수치만** 둔다.
 *
 * - [dailyInitialGenerationLimit]: 사용자당 하루 1차 생성 횟수 상한(이력서·포트폴리오 공통). 최소 1항목 성공 시 1회 차감.
 * - [dailyRegenerationLimitPerSection]: 생성 항목당 하루 재생성 횟수 상한. 사용자 요청 재생성 최종 성공 시 1회 차감
 *   (검증실패 자동 재시도는 미차감). 두 상한 모두 사용자 시간대 달력일(자정)에 리셋된다.
 *
 * 기본값(10·5)은 "정상 사용엔 충분하되 어뷰징·비용 폭증은 막는" MVP 적정선이다(§401: 수치는 운영 정책으로 조정).
 */
@ConfigurationProperties(prefix = "resumaker.quota")
data class GenerationQuotaProperties(
    val dailyInitialGenerationLimit: Int = 10,
    val dailyRegenerationLimitPerSection: Int = 5,
)
