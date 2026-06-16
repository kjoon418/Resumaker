package watson.resumaker.artifact.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.experience.domain.ExperienceRecordId
import java.util.UUID

class ArtifactVoTest {

    @Test
    fun FactToken은_공백을_거부한다() {
        assertThatThrownBy { FactToken.of("  ") }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun FactToken은_최대_길이를_초과하면_거부한다() {
        assertThatThrownBy { FactToken.of("a".repeat(FactToken.MAX_LENGTH + 1)) }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun FactToken은_정상값을_허용한다() {
        assertThat(FactToken.of("40%").value).isEqualTo("40%")
    }

    @Test
    fun SectionContent는_빈_내용을_허용한다() {
        // 부분 실패 항목은 내용이 비어 있을 수 있다.
        assertThatCode { SectionContent.of("") }.doesNotThrowAnyException()
    }

    @Test
    fun SectionContent는_최대_길이를_초과하면_거부한다() {
        // LOW-3 — DB 컬럼 길이와 동일하게 입력 시 검증
        assertThatThrownBy { SectionContent.of("a".repeat(SectionContent.MAX_LENGTH + 1)) }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun FactGrounding은_근거_문장이_최대_길이를_초과하면_거부한다() {
        // LOW-3 — evidenceText 입력 시 검증
        assertThatThrownBy {
            FactGrounding.create(
                token = FactToken.of("40%"),
                kind = FactKind.NUMERIC,
                sourceExperienceId = ExperienceRecordId(UUID.randomUUID()),
                evidenceText = "a".repeat(FactGrounding.MAX_EVIDENCE_LENGTH + 1),
            )
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun SnapshotSection은_빈_이름을_거부한다() {
        assertThatThrownBy { SnapshotSection.of("key", " ", SectionKind.SUMMARY, required = false) }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun SnapshotSection은_빈_정의_키를_거부한다() {
        assertThatThrownBy { SnapshotSection.of(" ", "이름", SectionKind.SUMMARY, required = false) }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun TemplateSnapshot은_빈_섹션_목록을_거부한다() {
        assertThatThrownBy { TemplateSnapshot.of(emptyList()) }
            .isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun TemplateSnapshot은_중복_정의_키를_거부한다() {
        assertThatThrownBy {
            TemplateSnapshot.of(
                listOf(
                    SnapshotSection.of("dup", "A", SectionKind.SUMMARY, required = false),
                    SnapshotSection.of("dup", "B", SectionKind.CAREER, required = false),
                ),
            )
        }.isInstanceOf(DomainValidationException::class.java)
    }

    @Test
    fun TemplateSnapshot은_키로_섹션을_찾는다() {
        val snapshot = TemplateSnapshot.of(
            listOf(
                SnapshotSection.of("summary", "요약", SectionKind.SUMMARY, required = true),
                SnapshotSection.of("career", "경력", SectionKind.CAREER, required = true),
            ),
        )
        assertThat(snapshot.sectionByKey("career")!!.name).isEqualTo("경력")
        assertThat(snapshot.sectionByKey("none")).isNull()
    }
}
