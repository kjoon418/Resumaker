package watson.resumaker.generation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.artifact.domain.FactKind
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.GenerationKind
import watson.resumaker.generation.application.GenerationMaterial
import watson.resumaker.generation.application.TargetSnapshot
import watson.resumaker.generation.application.TemplateSectionSpec
import watson.resumaker.target.domain.WritingStrategy
import java.time.Duration
import java.util.UUID

/**
 * [ClaudeCliArtifactGenerationAdapter] 단위 테스트. **실제 CLI 미호출** — fake [ProcessRunner]가 canned
 * envelope를 돌려준다. material→프롬프트 구성·결과 파싱→GenerationOutput 매핑(항목별·근거 포함)을 검증한다.
 */
class ClaudeCliArtifactGenerationAdapterTest {

    private val objectMapper = ObjectMapper()
    private val properties = ClaudeCliProperties()

    private val exp1 = ExperienceRecordId(UUID.randomUUID())
    private val exp2 = ExperienceRecordId(UUID.randomUUID())

    /** capturedStdin으로 프롬프트(가드레일 문구 포함)를 들여다본다. */
    private class CapturingRunner(private val resultJson: String) : ProcessRunner {
        var capturedStdin: String = ""
        private val mapper = ObjectMapper()

        override fun run(command: List<String>, stdin: String, timeout: Duration): ProcessResult {
            capturedStdin = stdin
            // 실측 envelope: 구조화 결과는 structured_output(이미 파싱된 JSON), result는 빈 문자열.
            val envelope = mapper.writeValueAsString(
                mapOf(
                    "type" to "result",
                    "is_error" to false,
                    "result" to "",
                    "structured_output" to mapper.readTree(resultJson),
                ),
            )
            return ProcessResult(0, envelope, "")
        }
    }

    private fun adapter(resultJson: String): Pair<ClaudeCliArtifactGenerationAdapter, CapturingRunner> {
        val runner = CapturingRunner(resultJson)
        val cli = ClaudeCliClient(runner, properties, objectMapper)
        return ClaudeCliArtifactGenerationAdapter(cli) to runner
    }

    private fun experienceSnapshot(id: ExperienceRecordId, title: String) = ExperienceSnapshot(
        id = id,
        title = title,
        body = "본문 $title",
        situation = null,
        action = null,
        result = null,
        skillTags = listOf("Kotlin"),
    )

    private val target = TargetSnapshot(recruitDirection = "백엔드 신입", company = "토스", job = "서버 개발자")

    @Test
    fun 이력서_프롬프트는_SUMMARY_섹션에_자기소개_초안_작성을_지시한다() {
        // given — 개인 신상 정보가 없어도 채용 방향으로 자기소개(요약) 초안을 만들도록 지시한다(자기소개 개선 C+D).
        val (adapter, runner) = adapter("""{"sections":[]}""")
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "요약경험")),
            target = target,
            templateSections = listOf(
                TemplateSectionSpec("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
            ),
            selectedExperienceIds = emptyList(),
        )

        // when
        adapter.generate(material)

        // then
        assertThat(runner.capturedStdin).contains("자기소개 초안")
    }

    @Test
    fun 이력서_생성_결과를_항목과_근거로_매핑한다() {
        // given — CLI가 한 섹션을 근거와 함께 돌려줬다고 가정한다.
        val resultJson = """
            {
              "sections": [
                {
                  "definitionKey": "section-0-요약",
                  "sectionKind": "SUMMARY",
                  "content": "백엔드 신입 요약",
                  "succeeded": true,
                  "sourceExperienceIds": ["${exp1.value}"],
                  "factGroundings": [
                    {"token": "Kotlin", "kind": "PROPER_NOUN", "sourceExperienceId": "${exp1.value}", "evidenceText": "본문에 Kotlin"}
                  ]
                }
              ]
            }
        """.trimIndent()
        val (adapter, _) = adapter(resultJson)
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "요약경험")),
            target = target,
            templateSections = listOf(
                TemplateSectionSpec("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
            ),
            selectedExperienceIds = emptyList(),
        )

        // when
        val output = adapter.generate(material)

        // then
        assertThat(output.sections).hasSize(1)
        val section = output.sections.first()
        assertThat(section.definitionKey).isEqualTo("section-0-요약")
        assertThat(section.sectionKind).isEqualTo(SectionKind.SUMMARY)
        assertThat(section.succeeded).isTrue()
        assertThat(section.sourceExperienceIds).containsExactly(exp1)
        assertThat(section.factGroundings).hasSize(1)
        assertThat(section.factGroundings.first().kind).isEqualTo(FactKind.PROPER_NOUN)
        assertThat(section.factGroundings.first().token).isEqualTo("Kotlin")
    }

    @Test
    fun 프롬프트에_신뢰성_가드레일과_경험_근거가_포함된다() {
        // given
        val (adapter, runner) = adapter("""{"sections":[]}""")
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "경험A")),
            target = target,
            templateSections = listOf(
                TemplateSectionSpec("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
            ),
            selectedExperienceIds = emptyList(),
        )

        // when
        adapter.generate(material)

        // then — 날조 금지·근거 경험 0 섹션 미생성·경험 id가 프롬프트에 들어간다.
        assertThat(runner.capturedStdin).contains("지어내지 마세요")
        assertThat(runner.capturedStdin).contains("근거 경험이 0인 섹션은 항목을 생성하지 마세요")
        assertThat(runner.capturedStdin).contains(exp1.value.toString())
    }

    @Test
    fun 이력서_프롬프트에_품질_지침_강한동사_버즈워드절제_간결_어조가_포함된다() {
        // given (AI-05) — 저비용 품질 보강이 1차 생성 프롬프트에 실린다(신뢰성 규칙 하위).
        val (adapter, runner) = adapter("""{"sections":[]}""")
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "경험A")),
            target = target,
            templateSections = listOf(
                TemplateSectionSpec("section-0-요약", "요약", SectionKind.SUMMARY, required = true),
            ),
            selectedExperienceIds = emptyList(),
        )

        // when
        adapter.generate(material)

        // then — 강한 동사·버즈워드 절제·간결·어조 일관 지침이 모두 들어간다.
        assertThat(runner.capturedStdin).contains("작성 품질 지침")
        assertThat(runner.capturedStdin).contains("담당했다")
        assertThat(runner.capturedStdin).contains("버즈워드")
        assertThat(runner.capturedStdin).contains("간결")
        assertThat(runner.capturedStdin).contains("어조")
    }

    @Test
    fun 항목_단위_부분_실패가_succeeded_false로_매핑된다() {
        // given (수용 기준 9)
        val resultJson = """
            {
              "sections": [
                {"definitionKey": "k1", "sectionKind": "CAREER", "content": "성공", "succeeded": true, "sourceExperienceIds": [], "factGroundings": []},
                {"definitionKey": "k2", "sectionKind": "CAREER", "content": "", "succeeded": false, "sourceExperienceIds": [], "factGroundings": []}
              ]
            }
        """.trimIndent()
        val (adapter, _) = adapter(resultJson)
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "경험A")),
            target = target,
            templateSections = listOf(
                TemplateSectionSpec("k1", "경력1", SectionKind.CAREER, required = true),
                TemplateSectionSpec("k2", "경력2", SectionKind.CAREER, required = false),
            ),
            selectedExperienceIds = emptyList(),
        )

        // when
        val output = adapter.generate(material)

        // then
        assertThat(output.sections.map { it.succeeded }).containsExactly(true, false)
    }

    @Test
    fun succeeded_누락이면_실패로_매핑된다() {
        // given (MED-2) — 모호한 출력의 안전 기본은 실패다.
        val resultJson = """
            {
              "sections": [
                {"definitionKey": "k1", "sectionKind": "CAREER", "content": "내용", "sourceExperienceIds": [], "factGroundings": []}
              ]
            }
        """.trimIndent()
        val (adapter, _) = adapter(resultJson)
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "경험A")),
            target = target,
            templateSections = listOf(
                TemplateSectionSpec("k1", "경력1", SectionKind.CAREER, required = true),
            ),
            selectedExperienceIds = emptyList(),
        )

        // when
        val output = adapter.generate(material)

        // then — succeeded 필드가 없으면 false로 매핑된다.
        assertThat(output.sections).hasSize(1)
        assertThat(output.sections.first().succeeded).isFalse()
    }

    @Test
    fun 작성_전략이_있으면_프롬프트에_전략을_싣고_원문_채용방향은_빼고_쓴다() {
        // given — 전략이 실린 목표(생성 시점 READY). 원문 대신 전략 블록으로 작성하게 한다.
        val (adapter, runner) = adapter("""{"sections":[]}""")
        val strategyTarget = TargetSnapshot(
            recruitDirection = "원문 채용 방향 전체 텍스트(복리후생 등 포함)",
            company = "토스",
            job = "서버 개발자",
            writingStrategy = WritingStrategy(
                keywords = listOf("대용량 트래픽"),
                tone = "담백하고 성과 중심",
                emphasize = listOf("백엔드 설계"),
                avoid = listOf("과장"),
                summary = "백엔드 신입 — 처리 역량 강조",
            ),
        )
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "경험A")),
            target = strategyTarget,
            templateSections = listOf(TemplateSectionSpec("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
            selectedExperienceIds = emptyList(),
        )

        // when
        adapter.generate(material)

        // then — 전략 블록 주입, 원문은 미포함.
        assertThat(runner.capturedStdin).contains("## 작성 전략(이 방향으로 작성)")
        assertThat(runner.capturedStdin).contains("백엔드 신입 — 처리 역량 강조")
        assertThat(runner.capturedStdin).contains("대용량 트래픽")
        assertThat(runner.capturedStdin).doesNotContain("원문 채용 방향 전체 텍스트")
    }

    @Test
    fun 작성_전략이_없으면_원문_채용방향을_그대로_쓴다() {
        // given — 전략 없음(추출 전·실패·진행 중) → 원문 폴백.
        val (adapter, runner) = adapter("""{"sections":[]}""")
        val material = GenerationMaterial(
            kind = GenerationKind.RESUME,
            experiences = listOf(experienceSnapshot(exp1, "경험A")),
            target = target, // writingStrategy = null
            templateSections = listOf(TemplateSectionSpec("section-0-요약", "요약", SectionKind.SUMMARY, required = true)),
            selectedExperienceIds = emptyList(),
        )

        // when
        adapter.generate(material)

        // then — 원문 블록 사용, 전략 블록은 미포함.
        assertThat(runner.capturedStdin).contains("## 목표 정보(채용 방향)")
        assertThat(runner.capturedStdin).contains("백엔드 신입")
        assertThat(runner.capturedStdin).doesNotContain("## 작성 전략(이 방향으로 작성)")
    }

    @Test
    fun 포트폴리오_프롬프트는_경험당_서사_1개를_지시한다() {
        // given (도메인 이해 §357)
        val (adapter, runner) = adapter("""{"sections":[]}""")
        val material = GenerationMaterial(
            kind = GenerationKind.PORTFOLIO,
            experiences = listOf(experienceSnapshot(exp1, "경험A"), experienceSnapshot(exp2, "경험B")),
            target = target,
            templateSections = emptyList(),
            selectedExperienceIds = listOf(exp1, exp2),
        )

        // when
        adapter.generate(material)

        // then
        assertThat(runner.capturedStdin).contains("경험당 서사 1개")
        assertThat(runner.capturedStdin).contains("EXPERIENCE_NARRATIVE")
        assertThat(runner.capturedStdin).contains(exp1.value.toString())
        assertThat(runner.capturedStdin).contains(exp2.value.toString())
    }
}
