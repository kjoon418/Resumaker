package watson.resumaker.auth.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import watson.resumaker.common.presentation.ErrorResponse

/**
 * CSRF 방어(cross-site 쿠키 인증용). 상태 변경 메서드(POST/PUT/PATCH/DELETE)는 커스텀 헤더
 * [REQUIRED_HEADER]를 반드시 포함해야 한다. 없으면 403으로 막는다.
 *
 * **왜 안전한가:** 브라우저는 교차 오리진 요청에 커스텀 헤더를 붙이려면 CORS 프리플라이트 승인을 받아야 하는데,
 * 우리 CORS는 허용된 앱 오리진만 승인한다(WebConfig). 따라서 악성 사이트는 피해자의 인증 쿠키가 자동 첨부되더라도
 * 이 헤더를 실은 요청을 보낼 수 없다(프리플라이트 차단). cross-site에서는 API 도메인이 심은 CSRF 쿠키를 다른
 * 오리진 JS가 읽지 못해 double-submit이 불가하므로, 커스텀 헤더 방식(OWASP 권장)을 택한다.
 *
 * 안전 메서드(GET/HEAD/OPTIONS)와 프리플라이트(OPTIONS)는 검사하지 않는다(CORS가 OPTIONS를 처리).
 */
class CsrfFilter(
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (requiresCheck(request) && request.getHeader(REQUIRED_HEADER).isNullOrBlank()) {
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(
                objectMapper.writeValueAsString(
                    ErrorResponse(
                        code = "CSRF_FORBIDDEN",
                        message = "요청을 처리할 수 없어요. 새로고침 후 다시 시도해 주세요.",
                    ),
                ),
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun requiresCheck(request: HttpServletRequest): Boolean =
        request.method.uppercase() in MUTATING_METHODS

    companion object {
        /** 모든 상태 변경 요청에 요구하는 커스텀 헤더(존재만으로 충분 — 교차 오리진 위조 차단). */
        const val REQUIRED_HEADER = "X-Requested-With"
        private val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
