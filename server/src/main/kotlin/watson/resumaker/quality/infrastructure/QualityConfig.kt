package watson.resumaker.quality.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 품질 개선 빈 설정. 개선 기준 사전·임계값([QualityCriteriaProperties])을 @ConfigurationProperties로 바인딩한다
 * (오너 큐레이션 시 application.yml에서 사전·임계값 교체 — 기획 §5.2-2).
 */
@Configuration
@EnableConfigurationProperties(
    QualityCriteriaProperties::class,
)
class QualityConfig
