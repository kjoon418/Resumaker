package watson.resumaker.generation.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import watson.resumaker.generation.application.GenerationJobProperties
import java.time.Clock

/**
 * 생성 사이클(B) 빈 설정.
 * - [ClaudeCliProperties] 활성화(@ConfigurationProperties 바인딩).
 * - [Clock]: 생성 시각(createdAt) 발급원. 테스트에서 고정 Clock으로 결정성을 확보할 수 있게 빈으로 둔다.
 * - [TransactionTemplate]: 외부 호출 전후로 트랜잭션을 짧게 끊기 위한 프로그래매틱 트랜잭션 경계(설계 §5).
 */
@Configuration
@EnableConfigurationProperties(
    ClaudeCliProperties::class,
    LlmProperties::class,
    AnthropicApiProperties::class,
    ArtifactVersioningProperties::class,
    GenerationQuotaProperties::class,
    GenerationJobProperties::class,
)
class GenerationConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun generationTransactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)
}
