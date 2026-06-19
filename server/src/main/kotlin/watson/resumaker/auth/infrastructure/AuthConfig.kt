package watson.resumaker.auth.infrastructure

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 인증 모듈 설정. [AuthTokenProperties] 바인딩을 활성화한다(@ConfigurationProperties).
 */
@Configuration
@EnableConfigurationProperties(AuthTokenProperties::class)
class AuthConfig
