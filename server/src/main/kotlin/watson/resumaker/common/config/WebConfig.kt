package watson.resumaker.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 웹 계층 설정. 브라우저 클라이언트(Compose Wasm 웹앱)가 다른 오리진에서 API를 호출할 수 있도록 CORS를 연다.
 *
 * MVP는 인증을 X-User-Id 커스텀 헤더로 하므로 자격증명(쿠키)은 쓰지 않는다(allowCredentials=false).
 * 허용 오리진은 환경설정(resumaker.cors.allowed-origin-patterns)으로 주입하며, 기본값은 로컬 개발 오리진이다.
 */
@Configuration
class WebConfig(
    @Value("\${resumaker.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private val allowedOriginPatterns: List<String>,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOriginPatterns.toTypedArray())
            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
    }
}
