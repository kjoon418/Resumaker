package watson.resumaker.auth.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * [CsrfFilter] 단위 테스트. 상태 변경 요청은 커스텀 헤더가 있어야 통과하고, 없으면 403으로 막힌다.
 * 안전 메서드(GET)는 헤더 없이도 통과한다.
 */
class CsrfFilterTest {

    private val filter = CsrfFilter(ObjectMapper())

    @Test
    fun 상태변경_요청에_커스텀헤더가_없으면_403으로_막는다() {
        val request = MockHttpServletRequest("POST", "/experiences")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(403, response.status)
        assertNull(chain.request) // 다음 필터로 진행하지 않았다.
    }

    @Test
    fun 상태변경_요청에_커스텀헤더가_있으면_통과한다() {
        val request = MockHttpServletRequest("POST", "/experiences")
        request.addHeader(CsrfFilter.REQUIRED_HEADER, "XMLHttpRequest")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request) // 다음 필터로 진행했다.
    }

    @Test
    fun 안전한_GET_요청은_헤더없이도_통과한다() {
        val request = MockHttpServletRequest("GET", "/experiences")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
    }
}
