package watson.resumaker.common.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * CORS 설정 검증. 브라우저 클라이언트가 보낼 프리플라이트(OPTIONS)에 허용 오리진·메서드가 응답되는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebConfigCorsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun 허용_오리진의_프리플라이트에_CORS_헤더를_응답한다() {
        mockMvc.perform(
            options("/experiences")
                .header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "GET"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
            .andExpect(header().exists("Access-Control-Allow-Methods"))
    }

    @Test
    fun 항목_직접_편집_PUT_프리플라이트를_허용한다() {
        mockMvc.perform(
            options("/artifacts/x/sections/y/content")
                .header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "PUT"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
    }

    @Test
    fun 허용되지_않은_오리진의_프리플라이트는_거부한다() {
        mockMvc.perform(
            options("/experiences")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"),
        )
            .andExpect(status().isForbidden)
    }
}
