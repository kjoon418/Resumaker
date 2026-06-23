package watson.resumaker.quality.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import watson.resumaker.artifact.domain.SectionKind
import watson.resumaker.experience.domain.ExperienceRecordId
import watson.resumaker.generation.application.DeterministicGroundingValidator
import watson.resumaker.generation.application.ExperienceSnapshot
import watson.resumaker.generation.application.FactTokenExtractor
import watson.resumaker.generation.application.GeneratedSection
import watson.resumaker.generation.application.TargetSnapshot
import java.util.UUID

/**
 * [QualityImprovementProcessor] 단위 테스트(QC3·QC4·QC9). 포트는 시밍(seam)해 **비용 0**으로 검증 흐름만 본다.
 * 검증기는 실제 결정적 구현을 쓴다(순수).
 *
 * 검증: 검증 통과 후보는 그대로 반환, 근거 없는 새 수치(QC3·QC9) 후보는 제외, 원본 사실 토큰 누락(QC4) 후보는
 * 제외, 검증실패는 자동 1회 재시도(두 번째가 통과하면 채택), 재시도도 실패하면 null(원본 유지).
 */
class QualityImprovementProcessorTest {

    private val extractor = FactTokenExtractor()
    private val groundingValidator = DeterministicGroundingValidator(extractor)
    private val preservationValidator = FactTokenPreservationValidator(extractor)

    private val expId = ExperienceRecordId(UUID.randomUUID())

    /** 호출마다 미리 큐에 담긴 후보를 순서대로 돌려주는 시밍 포트(재시도 시 두 번째 후보 검증). */
    private class QueuePort(private val outputs: MutableList<GeneratedSection?>) : QualityImprovementPort {
        var calls = 0
        override fun improve(input: QualityImprovementInput): GeneratedSection? {
            calls++
            return if (outputs.isEmpty()) null else outputs.removeAt(0)
        }
    }

    private fun input(original: String) = QualityImprovementInput(
        definitionKey = "section-0-요약",
        sectionKind = SectionKind.SUMMARY,
        originalContent = original,
        criteria = listOf("약한 동사를 강한 동사로"),
        target = TargetSnapshot("백엔드 신입", null, null),
        experiences = listOf(
            ExperienceSnapshot(expId, "경험", "초당 500건을 Kotlin으로 처리했다.", null, null, null, emptyList()),
        ),
        sourceExperienceIds = listOf(expId),
    )

    private fun candidate(content: String, succeeded: Boolean = true, key: String = "section-0-요약") = GeneratedSection(
        definitionKey = key,
        sectionKind = SectionKind.SUMMARY,
        content = content,
        succeeded = succeeded,
        sourceExperienceIds = listOf(expId),
        factGroundings = emptyList(),
    )

    @Test
    fun 검증을_모두_통과한_후보는_그대로_반환한다() {
        // given — 원본 사실(500·Kotlin)을 보존하고 새 사실을 더하지 않은 다듬기.
        val port = QueuePort(mutableListOf(candidate("Kotlin으로 초당 500건을 안정적으로 처리했어요.")))
        val processor = QualityImprovementProcessor(port, groundingValidator, preservationValidator)

        // when
        val result = processor.process(input("500건을 Kotlin으로 담당했다."))

        // then
        assertThat(result).isNotNull
        assertThat(port.calls).isEqualTo(1)
    }

    @Test
    fun 근거_없는_새_수치를_더한_후보는_제외된다_QC3_QC9() {
        // given — 출처에 없는 "30%"를 새로 끼워넣었다. 두 번 다 같은 위반 → 재시도도 실패.
        val port = QueuePort(
            mutableListOf(
                candidate("Kotlin으로 초당 500건을 처리해 성능을 30% 높였어요."),
                candidate("Kotlin으로 초당 500건을 처리해 성능을 30% 높였어요."),
            ),
        )
        val processor = QualityImprovementProcessor(port, groundingValidator, preservationValidator)

        // when
        val result = processor.process(input("500건을 Kotlin으로 처리했다."))

        // then — 근거 없는 30%가 검출돼 제외, 자동 1회 재시도 후 null.
        assertThat(result).isNull()
        assertThat(port.calls).isEqualTo(2)
    }

    @Test
    fun 원본_사실_토큰을_흘린_후보는_제외된다_QC4() {
        // given — 원본의 수치 500을 후보가 빠뜨렸다(다듬다 사실 누락). 두 번 다 누락 → null.
        val port = QueuePort(
            mutableListOf(
                candidate("Kotlin으로 많은 요청을 처리했어요."),
                candidate("Kotlin으로 많은 요청을 처리했어요."),
            ),
        )
        val processor = QualityImprovementProcessor(port, groundingValidator, preservationValidator)

        // when
        val result = processor.process(input("초당 500건을 Kotlin으로 처리했다."))

        // then — 500 누락으로 보존 검증 실패 → 제외.
        assertThat(result).isNull()
        assertThat(port.calls).isEqualTo(2)
    }

    @Test
    fun 첫_시도_검증실패해도_재시도가_통과하면_채택한다() {
        // given — 첫 후보는 500 누락(QC4 실패), 두 번째는 보존.
        val port = QueuePort(
            mutableListOf(
                candidate("Kotlin으로 많은 요청을 처리했어요."),
                candidate("Kotlin으로 초당 500건을 처리했어요."),
            ),
        )
        val processor = QualityImprovementProcessor(port, groundingValidator, preservationValidator)

        // when
        val result = processor.process(input("초당 500건을 Kotlin으로 처리했다."))

        // then — 자동 1회 재시도가 통과 → 채택.
        assertThat(result).isNotNull
        assertThat(result!!.content).contains("500")
        assertThat(port.calls).isEqualTo(2)
    }

    @Test
    fun 재시도는_딱_한_번이다_이중비용_금지() {
        // given — 계속 실패하는 후보. 호출은 정확히 2회(첫 시도 + 자동 1회)여야 한다.
        val port = QueuePort(
            mutableListOf(
                candidate("많은 요청을 처리했어요."),
                candidate("많은 요청을 처리했어요."),
                candidate("초당 500건을 처리했어요."), // 세 번째는 절대 호출되면 안 됨.
            ),
        )
        val processor = QualityImprovementProcessor(port, groundingValidator, preservationValidator)

        // when
        val result = processor.process(input("초당 500건을 처리했다."))

        // then
        assertThat(result).isNull()
        assertThat(port.calls).isEqualTo(2)
    }

    @Test
    fun 생성_실패_후보는_제외된다() {
        // given — succeeded=false. 두 번 다 실패.
        val port = QueuePort(mutableListOf(candidate("", succeeded = false), candidate("", succeeded = false)))
        val processor = QualityImprovementProcessor(port, groundingValidator, preservationValidator)

        // when and then
        assertThat(processor.process(input("500건을 처리했다."))).isNull()
    }
}
