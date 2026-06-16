package watson.resumaker.template.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TemplatePresetProviderTest {

    private val provider = TemplatePresetProvider()

    @Test
    fun 프리셋_목록은_비어있지_않다() {
        val presets = provider.getAll()
        assertThat(presets).isNotEmpty
    }

    @Test
    fun 각_프리셋은_key_name_섹션을_가진다() {
        provider.getAll().forEach { preset ->
            assertThat(preset.key).isNotBlank()
            assertThat(preset.name).isNotBlank()
            assertThat(preset.sections).isNotEmpty
        }
    }

    @Test
    fun 각_프리셋의_모든_섹션은_이름이_비어있지_않다() {
        provider.getAll().forEach { preset ->
            preset.sections.forEach { section ->
                assertThat(section.name).isNotBlank()
            }
        }
    }

    @Test
    fun key로_프리셋을_찾을_수_있다() {
        val first = provider.getAll().first()
        val found = provider.findByKey(first.key)
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo(first.name)
    }

    @Test
    fun 없는_key는_null을_반환한다() {
        assertThat(provider.findByKey("nonexistent-key")).isNull()
    }
}
