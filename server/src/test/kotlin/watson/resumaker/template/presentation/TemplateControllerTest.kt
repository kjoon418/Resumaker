package watson.resumaker.template.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import watson.resumaker.account.application.CurrentUserProvider
import watson.resumaker.account.domain.UserId
import watson.resumaker.template.application.CreateTemplateCommand
import watson.resumaker.template.application.TemplateService
import watson.resumaker.template.domain.SectionCharacter
import java.util.UUID

@WebMvcTest(TemplateController::class)
class TemplateControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var templateService: TemplateService

    @MockitoBean
    private lateinit var templateMapper: TemplateMapper

    @MockitoBean
    private lateinit var currentUserProvider: CurrentUserProvider

    @Test
    fun 양식_생성_요청이_성공하면_201과_양식을_반환한다() {
        // given
        whenever(currentUserProvider.currentUserId()).thenReturn(UserId(UUID.randomUUID()))
        whenever(templateMapper.toCreateCommand(any())).thenReturn(
            CreateTemplateCommand(name = mockName(), sections = emptyList()),
        )
        whenever(templateService.create(any(), any())).thenReturn(
            TemplateResponse(
                id = TEMPLATE_ID,
                name = "기본 이력서",
                sections = listOf(SectionResponse("한 줄 자기소개", SectionCharacter.SUMMARY, true)),
            ),
        )
        val request = CreateTemplateRequest(
            name = "기본 이력서",
            sections = listOf(SectionRequest("한 줄 자기소개", SectionCharacter.SUMMARY, true)),
        )

        // when and then
        mockMvc.post("/resume-templates") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(TEMPLATE_ID) }
            jsonPath("$.sections[0].character") { value("SUMMARY") }
        }
    }

    @Test
    fun 양식_이름이_누락되면_4XX를_반환한다() {
        // given
        val request = CreateTemplateRequest(
            name = null,
            sections = listOf(SectionRequest("요약", SectionCharacter.SUMMARY, false)),
        )

        // when and then
        mockMvc.post("/resume-templates") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun 섹션이_비어_있으면_4XX를_반환한다() {
        // given
        val request = CreateTemplateRequest(name = "이름", sections = emptyList())

        // when and then
        mockMvc.post("/resume-templates") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun 섹션_이름이_누락되면_4XX를_반환한다() {
        // given
        val request = CreateTemplateRequest(
            name = "이름",
            sections = listOf(SectionRequest(null, SectionCharacter.SUMMARY, false)),
        )

        // when and then
        mockMvc.post("/resume-templates") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    private fun mockName() = watson.resumaker.template.domain.TemplateName("기본 이력서")

    companion object {
        private const val TEMPLATE_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
