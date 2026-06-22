package watson.resumaker.target.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import watson.resumaker.target.application.TargetStrategyProperties

/**
 * 목표(Target) 모듈 빈 설정.
 * - [TargetStrategyProperties] 활성화(@ConfigurationProperties 바인딩 — 작성 전략 추출 워커 수치).
 *
 * Clock·ObjectMapper는 각각 [watson.resumaker.generation.infrastructure.GenerationConfig]와 Spring Boot
 * 자동 구성이 제공하는 빈을 주입받는다(중복 정의하지 않는다).
 */
@Configuration
@EnableConfigurationProperties(TargetStrategyProperties::class)
class TargetConfig
