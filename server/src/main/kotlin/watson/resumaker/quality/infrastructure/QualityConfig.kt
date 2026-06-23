package watson.resumaker.quality.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import watson.resumaker.quality.application.QualityImprovementJobProperties

/**
 * 품질 개선 빈 설정. 개선 기준 사전·임계값([QualityCriteriaProperties])과 처치 작업 워커 수치
 * ([QualityImprovementJobProperties])를 @ConfigurationProperties로 바인딩한다(오너 큐레이션 시 application.yml에서
 * 사전·임계값·수치 교체 — 기획 §5.2). [org.springframework.transaction.support.TransactionTemplate]은 생성 설정이
 * 이미 빈으로 등록하므로(GenerationConfig) 워커가 타입으로 주입받는다.
 */
@Configuration
@EnableConfigurationProperties(
    QualityCriteriaProperties::class,
    QualityImprovementJobProperties::class,
)
class QualityConfig
