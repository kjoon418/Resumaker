package watson.resumaker.quality.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 품질 개선 일일 한도 수치 외부 설정(품질 개선 기획 §5.1-3·§5.2-1 — 항목 재생성 상한과 **별개의** 자체 일일 한도,
 * 구체 N회는 운영·예산 판단이라 설정으로 외부화하고 시드값으로 진행). [watson.resumaker.generation.infrastructure.GenerationQuotaProperties]와 동일 패턴.
 *
 * 카운트 대상·차감 시점(작업 성공 시 1회)·리셋 경계(사용자 시간대 달력일)는 코드 고정이고 여기서는 **수치만** 둔다.
 *
 * - [dailyQualityImprovementLimit]: 사용자당 하루 품질 개선 횟수 상한. 채택 가능 후보 ≥1로 작업이 성공하면 1회 차감.
 *
 * 기본값(5)은 "정상 사용엔 충분하되 비용 폭증은 막는" MVP 잠정 시드다(오너 큐레이션 시 교체 — §5.2-1).
 */
@ConfigurationProperties(prefix = "resumaker.quality-quota")
data class QualityQuotaProperties(
    val dailyQualityImprovementLimit: Int = 5,
)
