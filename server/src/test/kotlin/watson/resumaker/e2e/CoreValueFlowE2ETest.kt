package watson.resumaker.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import watson.resumaker.account.presentation.LoginRequest
import watson.resumaker.account.presentation.SignUpRequest
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.auth.application.AuthTokenStore
import watson.resumaker.auth.application.InMemoryAuthTokenStore
import watson.resumaker.auth.presentation.CsrfFilter
import watson.resumaker.experience.domain.ExperienceType
import watson.resumaker.experience.presentation.CreateExperienceRequest
import watson.resumaker.generation.application.ArtifactGenerationPort
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.generation.application.GenerationKind
import watson.resumaker.generation.application.GenerationMaterial
import watson.resumaker.generation.application.GenerationOutput
import watson.resumaker.generation.application.ResumeTemplateGeneration
import watson.resumaker.generation.application.ResumeTemplateGenerationInput
import watson.resumaker.generation.application.ResumeTemplateGenerator
import watson.resumaker.generation.presentation.EditSectionContentRequest
import watson.resumaker.generation.presentation.PortfolioGenerationRequest
import watson.resumaker.generation.presentation.RegenerateSectionRequest
import watson.resumaker.generation.presentation.ResumeGenerationRequest
import watson.resumaker.target.presentation.CreateTargetRequest
import watson.resumaker.template.application.ResumeTemplateInterpreter
import watson.resumaker.template.application.TemplateInterpretation
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.presentation.CreateTemplateRequest
import watson.resumaker.template.presentation.InterpretRequest
import watson.resumaker.template.presentation.SectionRequest
import java.util.UUID

/**
 * 핵심 가치 흐름 전 구간 E2E(도메인 이해 §55~65, 수용 기준 7·12·13·14). 풀 컨텍스트(@SpringBootTest)를 H2로 띄워
 * **컨트롤러 → 필터(인증·CSRF) → 서비스 → 트랜잭션 → JPA 영속**까지 실제 협력을 한 번에 검증한다.
 *
 * 슬라이스(@WebMvcTest) 테스트가 메우지 못하는 통합 경계(쿠키 인증 왕복, 트랜잭션 경계, 지연 로딩 변환, 소유 격리,
 * 재로그인 데이터 보존)를 회귀 안전망으로 고정하는 것이 목적이다.
 *
 * **결정적 더블(외부 비결정 요소만 교체):**
 * - [ArtifactGenerationPort] / [ResumeTemplateGenerator]: 외부 LLM(Claude CLI) 대신 결정적 페이크. 생성 콘텐츠는
 *   숫자·라틴 토큰·인용부호를 쓰지 않아 실제 [watson.resumaker.generation.application.DeterministicGroundingValidator]
 *   (운영 빈 그대로 사용)를 통과한다 — 자동 검증 파이프라인도 실제로 탄다.
 * - [AuthTokenStore]: Redis 대신 인메모리 페이크(토큰 발급·검증·폐기 로직은 실제 [TokenService]가 그대로 수행).
 *
 * 나머지(가드레일·매퍼·리포지토리·트랜잭션 템플릿 등)는 운영 빈을 그대로 쓴다.
 */
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@Import(CoreValueFlowE2ETest.E2ETestDoubles::class)
class CoreValueFlowE2ETest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun 기록_겨냥_생성_다듬기_활용의_핵심_흐름이_끝까지_동작한다() {
        // 1. 기록 — 가입(쿠키 발급) 후 경험 기록을 쌓는다.
        val cookies = signUp(uniqueEmail())
        val experienceId = createExperience(cookies)

        // 2. 겨냥 — 목표 정보를 입력한다.
        val targetId = createTarget(cookies)

        // 3. 생성 — 양식 미지정(AI 생성 양식 폴백) 이력서 1차 생성(수용 기준 7). 모두 성공이면 201.
        val generated = generateResume(cookies, experienceId, targetId)
        val artifactId = generated["artifactId"].asText()
        val firstVersionId = generated["activeVersionId"].asText()
        val firstSectionId = generated["sections"][0]["sectionId"].asText()
        assertTrue(generated["sections"].size() > 0, "양식 섹션별 생성 항목이 있어야 한다.")
        // 모든 생성 항목은 출처 경험 근거를 가진다(근거 없는 항목 0건 — 수용 기준 17·6).
        generated["sections"].forEach { section ->
            assertEquals("GENERATED", section["status"].asText())
            assertTrue(section["sourceExperienceIds"].any { it.asText() == experienceId })
        }

        // 4. 다듬기 — 한 항목만 재생성하면 새 활성 버전이 생긴다(수용 기준 10·19).
        val regenerated = postAuth(
            "/artifacts/$artifactId/sections/$firstSectionId/regenerate",
            cookies,
            RegenerateSectionRequest(directive = "더 짧게"),
        ).andExpect { status { isOk() } }.andReturn().body()
        val secondVersionId = regenerated["activeVersion"]["versionId"].asText()
        assertNotEquals(firstVersionId, secondVersionId, "재생성은 새 버전을 만들어 활성으로 전환한다.")

        // 직전 버전이 보존되어 비교·복원이 가능하다(수용 기준 11). 생성 순서(오래된→최신)로 2개.
        val versions = getAuth("/artifacts/$artifactId/versions", cookies)
            .andExpect { status { isOk() } }.andReturn().body()
        assertEquals(2, versions["versions"].size())
        assertEquals(secondVersionId, versions["activeVersionId"].asText())
        val olderVersionId = versions["versions"][0]["versionId"].asText()
        assertEquals(firstVersionId, olderVersionId)

        // 복원 = 활성 전환(도메인 이해 §277·§283). 이전 버전으로 되돌린다.
        postAuth("/artifacts/$artifactId/versions/$olderVersionId/restore", cookies, body = null)
            .andExpect {
                status { isOk() }
                jsonPath("$.activeVersion.versionId") { value(olderVersionId) }
            }

        // 5. 활용 — 저장된 산출물은 언제든 열람되고, 항목 텍스트(복사 대상)가 제공된다(수용 기준 12).
        val read = getAuth("/artifacts/$artifactId", cookies)
            .andExpect { status { isOk() } }.andReturn().body()
        assertTrue(read["activeVersion"]["sections"][0]["content"].asText().isNotBlank())
    }

    @Test
    fun 포트폴리오도_경험별_항목으로_생성되어_열람된다() {
        // 핵심 흐름의 포트폴리오 분기(도메인 이해 §227~252). 선택 경험당 서사 항목 1개로 1:1 대응한다.
        val cookies = signUp(uniqueEmail())
        val experienceId = createExperience(cookies)
        val targetId = createTarget(cookies)

        val generated = postAuth(
            "/artifacts/portfolio",
            cookies,
            PortfolioGenerationRequest(listOf(experienceId), targetId),
        ).andExpect { status { isCreated() } }.andReturn().body()

        assertEquals("PORTFOLIO", generated["kind"].asText())
        assertEquals(1, generated["sections"].size())
        assertEquals("EXPERIENCE_NARRATIVE", generated["sections"][0]["sectionKind"].asText())
    }

    @Test
    fun 재로그인_후에도_기존_경험과_산출물이_보존된다() {
        // 수용 기준 14(전반): 로그아웃 후 재로그인해도 기존 데이터가 동일하게 보존된다.
        val email = uniqueEmail()
        val first = signUp(email)
        val experienceId = createExperience(first)
        val targetId = createTarget(first)
        val artifactId = generateResume(first, experienceId, targetId)["artifactId"].asText()

        // 로그아웃(토큰 폐기·쿠키 제거).
        postAuth("/auth/logout", first, body = null).andExpect { status { isNoContent() } }

        // 재로그인 — 새 쿠키를 받는다.
        val second = login(email)

        // 기존 경험·산출물이 그대로 조회된다.
        val experiences = getAuth("/experiences", second)
            .andExpect { status { isOk() } }.andReturn().body()
        assertTrue(experiences.any { it["id"].asText() == experienceId }, "재로그인 후 경험이 보존되어야 한다.")
        getAuth("/artifacts/$artifactId", second).andExpect { status { isOk() } }
    }

    @Test
    fun 타인의_산출물에는_접근할_수_없다() {
        // 수용 기준 13: 한 사용자의 데이터는 다른 사용자에게 노출되지 않는다(미존재·타인 모두 404).
        val owner = signUp(uniqueEmail())
        val experienceId = createExperience(owner)
        val targetId = createTarget(owner)
        val artifactId = generateResume(owner, experienceId, targetId)["artifactId"].asText()

        val stranger = signUp(uniqueEmail())
        getAuth("/artifacts/$artifactId", stranger).andExpect { status { isNotFound() } }
    }

    @Test
    fun 계정을_삭제하면_세션이_무효화되고_재로그인이_불가능하다() {
        // 수용 기준 14(후반): 계정 삭제 후 해당 계정으로는 더 이상 로그인되지 않는다(귀속 데이터 함께 삭제).
        val email = uniqueEmail()
        val cookies = signUp(email)
        val experienceId = createExperience(cookies)
        val targetId = createTarget(cookies)
        generateResume(cookies, experienceId, targetId)

        // 계정 삭제(귀속 데이터 cascade 삭제 + 토큰 폐기 + 쿠키 제거).
        postDelete("/me", cookies).andExpect { status { isNoContent() } }

        // 삭제된 계정으로는 로그인되지 않는다.
        postAuth("/auth/login", emptyArray(), LoginRequest(email, PASSWORD))
            .andExpect { status { is4xxClientError() } }
    }

    @Test
    fun 직접_편집은_자동검증_없이_새_버전으로_저장된다() {
        // 도메인 이해 §267·§428, 수용 기준 10·19: 직접 편집은 후보 단계 없이 즉시 새 버전을 만들고(채택=1콜),
        // AI 생성과 달리 자동 신뢰성 검증을 적용하지 않는다(최종 책임은 사용자).
        val cookies = signUp(uniqueEmail())
        val experienceId = createExperience(cookies)
        val targetId = createTarget(cookies)
        val generated = generateResume(cookies, experienceId, targetId)
        val artifactId = generated["artifactId"].asText()
        val firstVersionId = generated["activeVersionId"].asText()
        val sectionId = generated["sections"][0]["sectionId"].asText()
        val definitionKey = generated["sections"][0]["definitionKey"].asText()

        // 근거 없는 수치("40%")를 포함해도 직접 편집은 검증되지 않으므로 그대로 GENERATED로 저장된다(§428).
        val editedContent = "응답 속도를 40% 개선한 경험을 제가 직접 풀어 썼어요."
        val edited = putAuth(
            "/artifacts/$artifactId/sections/$sectionId/content",
            cookies,
            EditSectionContentRequest(content = editedContent),
        ).andExpect { status { isOk() } }.andReturn().body()

        // 새 버전이 활성으로 전환된다(편집=즉시 채택).
        val newVersionId = edited["activeVersion"]["versionId"].asText()
        assertNotEquals(firstVersionId, newVersionId, "직접 편집은 새 버전을 만들어 활성으로 전환한다.")

        // 같은 섹션 정의(definitionKey)로 편집 내용이 그대로 반영되고 상태는 GENERATED다(편집 결과는 사용자 확정).
        val editedSection = edited["activeVersion"]["sections"].first { it["definitionKey"].asText() == definitionKey }
        assertEquals(editedContent, editedSection["content"].asText())
        assertEquals("GENERATED", editedSection["status"].asText())

        // 직전 버전이 보존되어 비교·복원이 가능하다(수용 기준 11).
        val versions = getAuth("/artifacts/$artifactId/versions", cookies)
            .andExpect { status { isOk() } }.andReturn().body()
        assertEquals(2, versions["versions"].size())
    }

    @Test
    fun 붙여넣기_양식은_해석_후_사용자_확정을_거쳐야_생성에_적용된다() {
        // 도메인 이해 §2.5, 수용 기준 24: 붙여넣기로 만든 양식은 AI 해석 결과를 사용자가 확정한 뒤에만 생성에 적용된다.
        val cookies = signUp(uniqueEmail())
        val experienceId = createExperience(cookies)
        val targetId = createTarget(cookies)

        // 1. 붙여넣기 해석 — 후보 섹션을 받는다. 이 단계는 영속하지 않는다(게이트 앞단).
        val interpreted = postAuth(
            "/resume-templates/interpret",
            cookies,
            InterpretRequest(text = "지원 동기와 주요 프로젝트를 적어 제출해 주세요."),
        ).andExpect { status { isOk() } }.andReturn().body()
        assertEquals("interpreted", interpreted["status"].asText())
        assertTrue(interpreted["sections"].size() > 0, "해석 후보 섹션이 있어야 한다.")

        // 게이트 검증: 해석만으로는 양식이 저장되지 않는다(확정 전엔 사용자 양식 목록이 비어 있다).
        val beforeConfirm = getAuth("/resume-templates", cookies)
            .andExpect { status { isOk() } }.andReturn().body()
        assertEquals(0, beforeConfirm.size(), "해석 결과는 사용자가 확정하기 전까지 양식으로 저장되지 않는다.")

        // 2. 사용자 확정 — 해석 후보를 그대로 양식으로 생성한다(POST /resume-templates).
        val sectionRequests = interpreted["sections"].map {
            SectionRequest(
                name = it["name"].asText(),
                character = SectionCharacter.valueOf(it["character"].asText()),
                required = it["required"].asBoolean(),
            )
        }
        val created = postAuth(
            "/resume-templates",
            cookies,
            CreateTemplateRequest(name = "확정한 회사 양식", sections = sectionRequests),
        ).andExpect { status { isCreated() } }.andReturn().body()
        val templateId = created["id"].asText()

        // 3. 확정한 양식으로 생성하면 사용자 지정 양식 경로(USER_SELECTED)로 적용되고, 양식 섹션 수만큼 항목이 만들어진다.
        val generated = postAuth(
            "/artifacts/resume",
            cookies,
            ResumeGenerationRequest(experienceIds = listOf(experienceId), targetId = targetId, templateId = templateId),
        ).andExpect { status { isCreated() } }.andReturn().body()
        assertEquals("USER_SELECTED", generated["templateOrigin"].asText())
        assertEquals(sectionRequests.size, generated["sections"].size())
    }

    // ----- 흐름 헬퍼(실제 엔드포인트를 거친다) -----

    private fun signUp(email: String): Array<Cookie> =
        postAuth("/auth/signup", emptyArray(), SignUpRequest(email = email, password = PASSWORD))
            .andExpect { status { isCreated() } }
            .andReturn()
            .authCookies()

    private fun login(email: String): Array<Cookie> =
        postAuth("/auth/login", emptyArray(), LoginRequest(email = email, password = PASSWORD))
            .andExpect { status { isOk() } }
            .andReturn()
            .authCookies()

    private fun createExperience(cookies: Array<Cookie>): String =
        postAuth(
            "/experiences",
            cookies,
            CreateExperienceRequest(
                title = "결제 흐름 개선 경험",
                type = ExperienceType.PROJECT,
                body = "결제 과정에서 사용자들이 겪던 불편을 줄이려고 흐름을 다시 설계하고 다듬었어요.",
                detail = null,
            ),
        ).andExpect { status { isCreated() } }.andReturn().body()["id"].asText()

    private fun createTarget(cookies: Array<Cookie>): String =
        postAuth(
            "/targets",
            cookies,
            CreateTargetRequest(
                recruitDirection = "사용자 경험을 중요하게 여기는 서비스 개발 인재를 찾고 있어요.",
                companyName = null,
                jobTitle = null,
            ),
        ).andExpect { status { isCreated() } }.andReturn().body()["id"].asText()

    private fun generateResume(cookies: Array<Cookie>, experienceId: String, targetId: String): JsonNode =
        postAuth(
            "/artifacts/resume",
            cookies,
            ResumeGenerationRequest(experienceIds = listOf(experienceId), targetId = targetId, templateId = null),
        ).andExpect { status { isCreated() } }.andReturn().body()

    // ----- MockMvc 저수준 헬퍼(쿠키 + CSRF 헤더 부착) -----

    /** 상태 변경 요청: 인증 쿠키와 CSRF 커스텀 헤더([CsrfFilter.REQUIRED_HEADER])를 함께 싣는다. */
    private fun postAuth(path: String, cookies: Array<Cookie>, body: Any?): ResultActionsDsl =
        mockMvc.post(path) {
            header(CsrfFilter.REQUIRED_HEADER, "e2e")
            if (cookies.isNotEmpty()) cookie(*cookies)
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(body)
            }
        }

    /** 상태 변경(PUT) 요청: 쿠키와 CSRF 헤더를 함께 싣는다(직접 편집 등). */
    private fun putAuth(path: String, cookies: Array<Cookie>, body: Any?): ResultActionsDsl =
        mockMvc.put(path) {
            header(CsrfFilter.REQUIRED_HEADER, "e2e")
            if (cookies.isNotEmpty()) cookie(*cookies)
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(body)
            }
        }

    private fun postDelete(path: String, cookies: Array<Cookie>): ResultActionsDsl =
        mockMvc.delete(path) {
            header(CsrfFilter.REQUIRED_HEADER, "e2e")
            if (cookies.isNotEmpty()) cookie(*cookies)
        }

    /** 안전 메서드 조회: 쿠키만 싣는다(CSRF 검사는 상태 변경 메서드 한정). */
    private fun getAuth(path: String, cookies: Array<Cookie>): ResultActionsDsl =
        mockMvc.get(path) {
            if (cookies.isNotEmpty()) cookie(*cookies)
        }

    private fun MvcResult.body(): JsonNode =
        objectMapper.readTree(response.getContentAsString(Charsets.UTF_8))

    /** 응답에 실린 발급 쿠키(만료 쿠키 maxAge==0 제외)를 다음 요청에 재사용할 형태로 추린다. */
    private fun MvcResult.authCookies(): Array<Cookie> =
        response.cookies.filter { it.value.isNotBlank() && it.maxAge != 0 }.toTypedArray()

    private fun uniqueEmail(): String = "e2e-${UUID.randomUUID()}@example.com"

    /**
     * 외부 비결정 요소(LLM·Redis)만 결정적 더블로 교체한다. 운영 빈과 **같은 이름**으로 정의해 빈 정의 오버라이딩으로
     * 대체한다(`spring.main.allow-bean-definition-overriding=true`). @Primary 정렬에 의존하지 않아, 운영 어댑터가
     * @Primary든 아니든 결정적으로 교체된다(예: ClaudeCliResumeTemplateGenerator는 운영에서 @Primary다).
     */
    @TestConfiguration
    class E2ETestDoubles {

        /** 양식 섹션/선택 경험에 1:1로 대응하는 결정적 생성 결과. 콘텐츠는 숫자·라틴·인용부호가 없어 자동 검증을 통과한다. */
        @Bean("claudeCliArtifactGenerationAdapter")
        fun fakeGenerationPort(): ArtifactGenerationPort = object : ArtifactGenerationPort {
            override fun generate(material: GenerationMaterial): GenerationOutput {
                val sections = when (material.kind) {
                    GenerationKind.RESUME -> material.templateSections.map { spec ->
                        GeneratedSection(
                            definitionKey = spec.definitionKey,
                            sectionKind = spec.sectionKind,
                            content = "경험을 바탕으로 정리한 항목 내용이에요.",
                            succeeded = true,
                            sourceExperienceIds = material.experiences.map { it.id },
                            factGroundings = emptyList(),
                        )
                    }
                    GenerationKind.PORTFOLIO -> material.selectedExperienceIds.map { experienceId ->
                        GeneratedSection(
                            definitionKey = experienceId.value.toString(),
                            sectionKind = SectionKind.EXPERIENCE_NARRATIVE,
                            content = "선택한 경험을 서사로 풀어낸 내용이에요.",
                            succeeded = true,
                            sourceExperienceIds = listOf(experienceId),
                            factGroundings = emptyList(),
                        )
                    }
                }
                return GenerationOutput(sections)
            }
        }

        /** 양식 미지정 경로에서 AI 생성 양식 대신 기본 구조 폴백을 타게 한다(결정적). */
        @Bean("claudeCliResumeTemplateGenerator")
        fun fakeTemplateGenerator(): ResumeTemplateGenerator = object : ResumeTemplateGenerator {
            override fun generate(material: ResumeTemplateGenerationInput): ResumeTemplateGeneration =
                ResumeTemplateGeneration.Unavailable
        }

        /** Redis 없이 토큰 저장소를 인메모리로 대체한다(TokenService 로직은 실제 그대로 동작). */
        @Bean("redisAuthTokenStore")
        fun inMemoryAuthTokenStore(): AuthTokenStore = InMemoryAuthTokenStore()

        /**
         * 붙여넣기 양식 해석을 결정적으로 만든다(운영은 ClaudeCliResumeTemplateInterpreter). 운영 빈이 @Primary이고
         * UnavailableResumeTemplateInterpreter도 공존하므로, 이름 오버라이드 + @Primary로 모호성 없이 교체한다.
         */
        @Bean("claudeCliResumeTemplateInterpreter")
        @Primary
        fun fakeTemplateInterpreter(): ResumeTemplateInterpreter = object : ResumeTemplateInterpreter {
            override fun interpret(pastedText: String): TemplateInterpretation =
                TemplateInterpretation.Interpreted(
                    listOf(
                        SectionDefinition.of("지원 동기", SectionCharacter.SUMMARY, required = true),
                        SectionDefinition.of("주요 프로젝트", SectionCharacter.CAREER, required = true),
                    ),
                )
        }
    }

    companion object {
        private const val PASSWORD = "password1"
    }
}
