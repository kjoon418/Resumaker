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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
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
import watson.resumaker.generation.presentation.PortfolioGenerationRequest
import watson.resumaker.generation.presentation.ResumeGenerationRequest
import watson.resumaker.quality.application.QualityImprovementInput
import watson.resumaker.quality.application.QualityImprovementPort
import watson.resumaker.target.presentation.CreateTargetRequest
import java.util.UUID

/**
 * 품질 개선 핵심 흐름 전 구간 E2E(품질 개선 기획 §4.4, 수용 기준 QC3·QC4·QC5·QC7·QC8·QC10). 풀 컨텍스트(@SpringBootTest)를
 * H2로 띄워 **컨트롤러 → 필터(인증·CSRF) → 서비스 → 워커 → 트랜잭션 → JPA 영속**까지 실제 협력을 한 번에 검증한다.
 *
 * **메모리 교훈(ecfc116):** 202/200만 보고 통과하지 않고, 채택이 **새 활성 버전을 DB에 영속·조회까지** 했는지 끝까지
 * 확인한다(전 흐름 자동 E2E 부재가 산출물 미저장 버그를 놓쳤던 교훈 — 개선도 영속까지 닫는다).
 *
 * **결정적 더블(외부 비결정만 교체):**
 * - [ArtifactGenerationPort]: 1차 생성을 결정적 페이크로. 약한 동사("담당했다")를 담아 진단이 AUTO_REWRITE 소견을 내게 한다.
 * - [QualityImprovementPort]: 처치를 결정적 페이크로. 원본 사실 토큰을 보존하고 새 사실을 더하지 않는 후보를 돌려줘
 *   신뢰성 검증(QC3)·보존 검증(QC4)을 실제로 통과하게 한다(운영 검증 빈 그대로 사용).
 * - [AuthTokenStore]: Redis 대신 인메모리.
 */
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureMockMvc
@Import(QualityImprovementE2ETest.E2ETestDoubles::class)
class QualityImprovementE2ETest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var generationJobWorker: watson.resumaker.generation.application.GenerationJobWorker

    @Autowired
    private lateinit var qualityImprovementJobWorker: watson.resumaker.quality.application.QualityImprovementJobWorker

    @Test
    fun 진단_개선_채택_핵심흐름이_끝까지_동작하고_새_활성버전이_영속된다() {
        // 1. 준비 — 가입·경험·목표·1차 생성(약한 동사가 담긴 산출물).
        val cookies = signUp(uniqueEmail())
        val experienceId = createExperience(cookies)
        val targetId = createTarget(cookies)
        val artifactId = submitResumeAndAwait(cookies, experienceId, targetId)

        val before = getAuth("/artifacts/$artifactId", cookies).andExpect { status { isOk() } }.andReturn().body()
        val firstVersionId = before["activeVersion"]["versionId"].asText()
        val originalContent = before["activeVersion"]["sections"][0]["content"].asText()
        assertTrue(originalContent.contains("담당했다"), "진단 대상 약한 동사가 산출물에 있어야 한다.")

        // 2. 진단 — 품질 점검. 약한 동사가 AUTO_REWRITE 소견으로 잡힌다(QC1).
        val review = postAuth("/artifacts/$artifactId/quality-review", cookies, body = null)
            .andExpect { status { isOk() } }.andReturn().body()
        assertTrue(review["autoRewriteCount"].asInt() >= 1, "자동 적용 후보가 하나 이상이어야 한다.")
        val autoFinding = review["findings"].first { it["treatmentKind"].asText() == "AUTO_REWRITE" }
        val findingId = autoFinding["findingId"].asText()

        // 3. 개선 접수 — AUTO_REWRITE 소견을 골라 처치 작업을 접수한다(202).
        val submitted = postAuth(
            "/artifacts/$artifactId/quality-improvements",
            cookies,
            mapOf("findingIds" to listOf(findingId)),
        ).andExpect { status { isAccepted() } }.andReturn().body()
        val jobId = submitted["jobId"].asText()

        // 4. 폴링 — 워커를 구동해 SUCCEEDED·후보를 확인한다(QC3·QC4 통과분만 후보, QC7 차감은 작업 성공 시).
        val candidateId = awaitImprovedCandidate(cookies, artifactId, jobId)

        // 5. 채택 — 후보를 채택해 새 활성 버전을 만든다(QC5).
        val adopted = postAuth(
            "/artifacts/$artifactId/quality-improvements/$jobId/adopt",
            cookies,
            mapOf("candidateIds" to listOf(candidateId)),
        ).andExpect { status { isOk() } }.andReturn().body()
        val newVersionId = adopted["activeVersion"]["versionId"].asText()
        assertNotEquals(firstVersionId, newVersionId, "채택은 새 활성 버전을 만든다.")

        // 6. 영속 검증(ecfc116 교훈) — 다시 조회해 새 활성 버전이 DB에 남아 있고 내용이 다듬어졌는지 끝까지 본다.
        val after = getAuth("/artifacts/$artifactId", cookies).andExpect { status { isOk() } }.andReturn().body()
        assertEquals(newVersionId, after["activeVersion"]["versionId"].asText(), "새 활성 버전이 영속·조회되어야 한다.")
        val adoptedContent = after["activeVersion"]["sections"].first { it["content"].asText().isNotBlank() }["content"].asText()
        assertNotEquals(originalContent, adoptedContent, "채택 후 내용이 다듬어진 후보로 바뀌어야 한다.")
        // 직전 버전이 보존되어 비교·복원이 가능하다(QC5 — 새 버전 추가, 이전 버전 보존).
        val versions = getAuth("/artifacts/$artifactId/versions", cookies).andExpect { status { isOk() } }.andReturn().body()
        assertTrue(versions["versions"].size() >= 2, "채택은 새 버전을 더하고 직전 버전을 보존한다.")
    }

    @Test
    fun 포트폴리오_품질_점검은_거절된다_QC10() {
        // QC10 — 포트폴리오는 MVP 자동 개선 대상이 아니므로 진입점이 거절된다(서버단 RESUME 가드).
        val cookies = signUp(uniqueEmail())
        val experienceId = createExperience(cookies)
        val targetId = createTarget(cookies)

        val submitted = postAuth(
            "/artifacts/portfolio",
            cookies,
            PortfolioGenerationRequest(listOf(experienceId), targetId),
        ).andExpect { status { isAccepted() } }.andReturn().body()
        val artifactId = awaitArtifactId(cookies, submitted["jobId"].asText())

        // 포트폴리오 산출물 점검은 400(이력서 한정 가드).
        postAuth("/artifacts/$artifactId/quality-review", cookies, body = null)
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun 타인의_산출물은_점검할_수_없다_QC8() {
        // QC8 — 소유 격리.
        val owner = signUp(uniqueEmail())
        val experienceId = createExperience(owner)
        val targetId = createTarget(owner)
        val artifactId = submitResumeAndAwait(owner, experienceId, targetId)

        val stranger = signUp(uniqueEmail())
        postAuth("/artifacts/$artifactId/quality-review", stranger, body = null)
            .andExpect { status { isNotFound() } }
    }

    // ----- 흐름 헬퍼 -----

    private fun signUp(email: String): Array<Cookie> =
        postAuth("/auth/signup", emptyArray(), SignUpRequest(email = email, password = PASSWORD))
            .andExpect { status { isCreated() } }.andReturn().authCookies()

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

    private fun submitResumeAndAwait(cookies: Array<Cookie>, experienceId: String, targetId: String): String {
        val submitted = postAuth(
            "/artifacts/resume",
            cookies,
            ResumeGenerationRequest(experienceIds = listOf(experienceId), targetId = targetId, templateId = null),
        ).andExpect { status { isAccepted() } }.andReturn().body()
        return awaitArtifactId(cookies, submitted["jobId"].asText())
    }

    /** 1차 생성 워커를 구동해 작업을 SUCCEEDED로 만들고 artifactId를 돌려준다. */
    private fun awaitArtifactId(cookies: Array<Cookie>, jobId: String): String {
        repeat(20) {
            generationJobWorker.poll()
            val job = getAuth("/generation-jobs/$jobId", cookies).andExpect { status { isOk() } }.andReturn().body()
            when (job["status"].asText()) {
                "SUCCEEDED" -> return job["artifactId"].asText()
                "FAILED" -> error("1차 생성 작업이 실패했어요: ${job["errorCode"].asText()}")
            }
        }
        error("1차 생성 작업이 제한 시간 안에 완료되지 않았어요(jobId=$jobId).")
    }

    /** 품질 개선 워커를 구동해 작업을 SUCCEEDED로 만들고 첫 후보 candidateId를 돌려준다. */
    private fun awaitImprovedCandidate(cookies: Array<Cookie>, artifactId: String, jobId: String): String {
        repeat(20) {
            qualityImprovementJobWorker.poll()
            val job = getAuth("/artifacts/$artifactId/quality-improvements/$jobId", cookies)
                .andExpect { status { isOk() } }.andReturn().body()
            when (job["status"].asText()) {
                "SUCCEEDED" -> {
                    assertTrue(job["candidates"].size() >= 1, "성공한 작업은 채택 가능한 후보가 있어야 한다.")
                    return job["candidates"][0]["candidateId"].asText()
                }
                "FAILED" -> error("품질 개선 작업이 실패했어요: ${job["errorCode"].asText()} / ${job["errorMessage"].asText()}")
            }
        }
        error("품질 개선 작업이 제한 시간 안에 완료되지 않았어요(jobId=$jobId).")
    }

    // ----- MockMvc 저수준 헬퍼 -----

    private fun postAuth(path: String, cookies: Array<Cookie>, body: Any?): ResultActionsDsl =
        mockMvc.post(path) {
            header(CsrfFilter.REQUIRED_HEADER, "e2e")
            if (cookies.isNotEmpty()) cookie(*cookies)
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(body)
            }
        }

    private fun getAuth(path: String, cookies: Array<Cookie>): ResultActionsDsl =
        mockMvc.get(path) {
            if (cookies.isNotEmpty()) cookie(*cookies)
        }

    private fun MvcResult.body(): JsonNode =
        objectMapper.readTree(response.getContentAsString(Charsets.UTF_8))

    private fun MvcResult.authCookies(): Array<Cookie> =
        response.cookies.filter { it.value.isNotBlank() && it.maxAge != 0 }.toTypedArray()

    private fun uniqueEmail(): String = "quality-e2e-${UUID.randomUUID()}@example.com"

    /**
     * 외부 비결정 요소(LLM·Redis)만 결정적 더블로 교체한다(운영 빈과 같은 이름으로 정의해 오버라이딩).
     */
    @TestConfiguration
    class E2ETestDoubles {

        /** 1차 생성: 약한 동사("담당했다")를 담아 진단이 AUTO_REWRITE 소견을 내게 한다. 숫자·라틴은 없어 검증 통과. */
        @Bean("claudeCliArtifactGenerationAdapter")
        fun fakeGenerationPort(): ArtifactGenerationPort = object : ArtifactGenerationPort {
            override fun generate(material: GenerationMaterial): GenerationOutput {
                val sections = when (material.kind) {
                    GenerationKind.RESUME -> material.templateSections.map { spec ->
                        GeneratedSection(
                            definitionKey = spec.definitionKey,
                            sectionKind = spec.sectionKind,
                            content = "결제 시스템을 담당했다.",
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

        /** 처치: 원본 사실 토큰을 보존하고 새 사실을 더하지 않는 후보(검증 통과). 약한 동사를 강한 동사로 다듬는다. */
        @Bean("claudeCliQualityImprovementAdapter")
        fun fakeImprovementPort(): QualityImprovementPort = object : QualityImprovementPort {
            override fun improve(input: QualityImprovementInput): GeneratedSection = GeneratedSection(
                definitionKey = input.definitionKey,
                sectionKind = input.sectionKind,
                content = "결제 시스템을 설계하고 운영했어요.",
                succeeded = true,
                sourceExperienceIds = input.sourceExperienceIds,
                factGroundings = emptyList(),
            )
        }

        /** 양식 미지정 경로에서 기본 구조 폴백을 타게 한다(결정적). */
        @Bean("claudeCliResumeTemplateGenerator")
        fun fakeTemplateGenerator(): ResumeTemplateGenerator = object : ResumeTemplateGenerator {
            override fun generate(material: ResumeTemplateGenerationInput): ResumeTemplateGeneration =
                ResumeTemplateGeneration.Unavailable
        }

        /** Redis 없이 토큰 저장소를 인메모리로 대체한다. */
        @Bean("redisAuthTokenStore")
        fun inMemoryAuthTokenStore(): AuthTokenStore = InMemoryAuthTokenStore()
    }

    companion object {
        private const val PASSWORD = "password1"
    }
}
