package watson.resumaker.auth.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import watson.resumaker.account.domain.UserId
import watson.resumaker.auth.infrastructure.AuthTokenProperties
import java.util.UUID

/**
 * [TokenService] 단위 테스트. 발급·검증·회전·폐기 로직을 인메모리 페이크 저장소로 검증한다(실제 Redis 불필요).
 */
class TokenServiceTest {

    private val store = InMemoryAuthTokenStore()
    private val service = TokenService(store, AuthTokenProperties())

    private val userId = UserId(UUID.randomUUID())

    @Test
    fun `발급한 access 토큰으로 사용자를 해석한다`() {
        val issued = service.issue(userId)

        assertEquals(userId, service.authenticate(issued.accessToken))
    }

    @Test
    fun `access와 refresh 토큰은 서로 다르고 예측 불가한 값이다`() {
        val issued = service.issue(userId)

        assertNotEquals(issued.accessToken, issued.refreshToken)
        // 같은 사용자로 두 번 발급해도 토큰은 매번 달라야 한다(SecureRandom).
        assertNotEquals(issued.accessToken, service.issue(userId).accessToken)
    }

    @Test
    fun `알 수 없는 토큰은 인증되지 않는다`() {
        service.issue(userId)

        assertNull(service.authenticate("unknown-token"))
    }

    @Test
    fun `refresh는 새 토큰쌍을 발급하고 기존 refresh를 1회용으로 폐기한다`() {
        val issued = service.issue(userId)

        val rotated = service.refresh(issued.refreshToken)

        assertNotNull(rotated)
        // 새 access는 유효하고, 새 토큰들은 이전과 다르다.
        assertEquals(userId, service.authenticate(rotated!!.accessToken))
        assertNotEquals(issued.refreshToken, rotated.refreshToken)
        // 기존 refresh는 회전 후 재사용할 수 없다(탈취 재사용 차단).
        assertNull(service.refresh(issued.refreshToken))
    }

    @Test
    fun `무효한 refresh 토큰은 회전되지 않는다`() {
        assertNull(service.refresh("invalid-refresh"))
    }

    @Test
    fun `폐기하면 access와 refresh 모두 무효가 된다`() {
        val issued = service.issue(userId)

        service.revoke(issued.accessToken, issued.refreshToken)

        assertNull(service.authenticate(issued.accessToken))
        assertNull(service.refresh(issued.refreshToken))
    }
}
