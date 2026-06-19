package watson.resumaker.auth.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import watson.resumaker.auth.application.TokenService

/**
 * 인증/보안 서블릿 필터 등록. **@Component가 아니라 @Configuration + FilterRegistrationBean으로 등록**해,
 * 컨트롤러 @WebMvcTest 슬라이스에는 필터가 실리지 않게 한다(슬라이스 테스트는 CurrentUserProvider를 목으로
 * 대체하므로 필터·Redis가 불필요). 실제 앱과 풀 컨텍스트(@SpringBootTest)에서만 적용된다.
 *
 * 순서: 인증(1) → CSRF(2). 둘 다 모든 경로에 적용한다.
 */
@Configuration
class SecurityFilterConfig {

    @Bean
    fun tokenAuthenticationFilterRegistration(
        tokenService: TokenService,
        cookieService: AuthCookieService,
    ): FilterRegistrationBean<TokenAuthenticationFilter> =
        FilterRegistrationBean(TokenAuthenticationFilter(tokenService, cookieService)).apply {
            order = 1
            addUrlPatterns("/*")
        }

    @Bean
    fun csrfFilterRegistration(objectMapper: ObjectMapper): FilterRegistrationBean<CsrfFilter> =
        FilterRegistrationBean(CsrfFilter(objectMapper)).apply {
            order = 2
            addUrlPatterns("/*")
        }
}
