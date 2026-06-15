package watson.resumaker.template.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.UserId
import watson.resumaker.common.domain.ResourceNotFoundException
import watson.resumaker.template.domain.ResumeTemplate
import watson.resumaker.template.domain.ResumeTemplateId
import watson.resumaker.template.domain.SectionCharacter
import watson.resumaker.template.domain.SectionDefinition
import watson.resumaker.template.domain.TemplateName
import watson.resumaker.template.infrastructure.ResumeTemplateRepository
import java.util.UUID

class TemplateServiceTest {

    private val repository: ResumeTemplateRepository = mock()
    private val mapper = TemplateServiceMapper()
    private val service = TemplateService(repository, mapper)

    private val ownerId = UserId(UUID.randomUUID())

    private fun section(name: String, character: SectionCharacter = SectionCharacter.SUMMARY) =
        SectionDefinition.of(name, character, required = false)

    @Test
    fun 생성하면_저장된_양식을_응답으로_변환한다() {
        // given
        whenever(repository.save(any<ResumeTemplate>())).thenAnswer { it.arguments[0] }
        val command = CreateTemplateCommand(
            name = TemplateName("기본 이력서"),
            sections = listOf(section("한 줄 자기소개"), section("주요 경력", SectionCharacter.CAREER)),
        )

        // when
        val response = service.create(ownerId, command)

        // then
        assertThat(response.name).isEqualTo("기본 이력서")
        assertThat(response.sections.map { it.name }).containsExactly("한 줄 자기소개", "주요 경력")
        verify(repository).save(any<ResumeTemplate>())
    }

    @Test
    fun 단건_조회는_소유자_조건으로_가져온다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("내 양식"), listOf(section("요약")))
        whenever(repository.findByIdAndOwnerId(any(), any())).thenReturn(template)

        // when
        val response = service.getOne(ownerId, template.id)

        // then
        assertThat(response.name).isEqualTo("내 양식")
    }

    @Test
    fun 단건_조회는_정확한_id와_ownerId를_레포지토리에_전달한다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("내 양식"), listOf(section("요약")))
        var capturedId: Any? = null
        var capturedOwnerId: Any? = null

        // value class unboxing 우회: thenAnswer로 실제 전달된 인자를 직접 캡처한다.
        // Mockito ArgumentCaptor는 value class 인자를 JVM 언박싱 후 매처로 받아 캡처 타입이
        // 맞지 않아 실패할 수 있으므로 Answer를 사용한다.
        whenever(repository.findByIdAndOwnerId(any(), any())).thenAnswer { invocation ->
            capturedId = invocation.arguments[0]
            capturedOwnerId = invocation.arguments[1]
            template
        }

        // when
        service.getOne(ownerId, template.id)

        // then — 서비스가 입력받은 id·ownerId를 그대로 레포지토리에 전달하는지 검증한다.
        // Mockito는 value class 인자를 JVM 언박싱한 내부값(UUID)으로 전달하므로 .value로 대조한다.
        assertThat(capturedId).isEqualTo(template.id.value)
        assertThat(capturedOwnerId).isEqualTo(ownerId.value)
    }

    @Test
    fun 소유하지_않은_양식_조회는_찾을_수_없음_예외를_던진다() {
        // given
        val id = ResumeTemplateId(UUID.randomUUID())
        whenever(repository.findByIdAndOwnerId(any(), any())).thenReturn(null)

        // when and then
        assertThatThrownBy { service.getOne(ownerId, id) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun 소유하지_않은_양식_수정은_거부되고_저장되지_않는다() {
        // given
        val id = ResumeTemplateId(UUID.randomUUID())
        whenever(repository.findByIdAndOwnerId(any(), any())).thenReturn(null)
        val command = UpdateTemplateCommand(TemplateName("새 이름"), listOf(section("요약")))

        // when and then
        assertThatThrownBy { service.update(ownerId, id, command) }
            .isInstanceOf(ResourceNotFoundException::class.java)
        verify(repository, never()).save(any<ResumeTemplate>())
    }

    @Test
    fun 수정하면_이름과_섹션이_갱신된_응답을_반환한다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("기존"), listOf(section("기존 섹션")))
        whenever(repository.findByIdAndOwnerId(any(), any())).thenReturn(template)
        val command = UpdateTemplateCommand(
            name = TemplateName("새 이름"),
            sections = listOf(section("핵심 역량"), section("주요 경력", SectionCharacter.CAREER)),
        )

        // when
        val response = service.update(ownerId, template.id, command)

        // then
        assertThat(response.name).isEqualTo("새 이름")
        assertThat(response.sections.map { it.character })
            .containsExactly(SectionCharacter.SUMMARY, SectionCharacter.CAREER)
    }

    @Test
    fun 삭제는_소유한_양식을_레포지토리에서_제거한다() {
        // given
        val template = ResumeTemplate.create(ownerId, TemplateName("내 양식"), listOf(section("요약")))
        whenever(repository.findByIdAndOwnerId(any(), any())).thenReturn(template)

        // when
        service.delete(ownerId, template.id)

        // then
        verify(repository).delete(template)
    }
}
