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
        // 산출물 항목 직접 편집(PUT /artifacts/{id}/sections/{id}/content)을 포함해
        // 브라우저 클라이언트가 쓰는 모든 메서드를 허용한다(프론트 통합 Slice).
        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOriginPatterns.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            // 쿠키 기반 인증: 브라우저가 자격증명(HttpOnly 쿠키)을 교차 오리진으로 보내려면 credentials 허용 필요.
            // allowedOriginPatterns는 credentials와 함께 써도 ACAO를 구체 오리진으로 반사하므로 와일드카드 금지에 위배되지 않는다.
            .allowCredentials(true)
    }
}
