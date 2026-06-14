package watson.resumaker.validation

import kotlin.test.Test
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
    fun validUuidPasses() {
        assertNull(Validators.validateUserId("123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun nonUuidFails() {
        assertNotNull(Validators.validateUserId("not-a-uuid"))
        assertNotNull(Validators.validateUserId(""))
    }

    @Test
    fun requiredBlankFails() {
        assertNotNull(Validators.validateRequired("   ", "필요"))
    }

    @Test
    fun requiredFilledPasses() {
        assertNull(Validators.validateRequired("값", "필요"))
    }
}
