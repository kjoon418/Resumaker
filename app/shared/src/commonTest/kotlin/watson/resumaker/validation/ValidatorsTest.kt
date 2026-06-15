package watson.resumaker.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ValidatorsTest {

    @Test
    fun validEmailPasses() {
        assertNull(Validators.validateEmail("name@example.com"))
    }

    @Test
    fun emptyEmailFails() {
        assertNotNull(Validators.validateEmail(""))
    }

    @Test
    fun malformedEmailFails() {
        assertNotNull(Validators.validateEmail("name@"))
        assertNotNull(Validators.validateEmail("nameexample.com"))
        assertNotNull(Validators.validateEmail("name@example"))
    }

    @Test
    fun passwordUnderEightFails() {
        assertNotNull(Validators.validatePassword("1234567"))
    }

    @Test
    fun passwordEightOrMorePasses() {
        assertNull(Validators.validatePassword("12345678"))
    }

    @Test
    fun requiredBlankFails() {
        assertNotNull(Validators.validateRequired("   ", "필요"))
    }

    @Test
    fun requiredFilledPasses() {
        assertNull(Validators.validateRequired("값", "필요"))
    }

    // UX-7: 비밀번호 helper 실시간 피드백.
    @Test
    fun passwordHelperPromptsAtBoundaryMinusOne() {
        assertEquals("8자 이상으로 입력해 주세요.", Validators.passwordHelper("1234567", hasError = false))
    }

    @Test
    fun passwordHelperPromptsWhenTooShort() {
        assertEquals("8자 이상으로 입력해 주세요.", Validators.passwordHelper("123", hasError = false))
    }

    @Test
    fun passwordHelperTurnsPositiveAtEightChars() {
        assertEquals("사용할 수 있는 비밀번호예요.", Validators.passwordHelper("12345678", hasError = false))
    }

    @Test
    fun passwordHelperIsNullWhenErrorShown() {
        assertNull(Validators.passwordHelper("12345678", hasError = true))
    }
}
