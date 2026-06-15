package watson.resumaker.account.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.resumaker.account.domain.User
import java.util.UUID

/**
 * 사용자 영속성. 순수 DB 로직만 담당한다(검증 가이드).
 *
 * 식별자 타입은 UUID다. UserId(value class @Id)는 Hibernate가 내부 타입(UUID)으로 등록하므로
 * findById/deleteById/existsById는 userId.value(UUID)로 호출한다.
 */
interface UserRepository : JpaRepository<User, UUID> {

    fun existsByCredentialEmail(email: String): Boolean

    fun findByCredentialEmail(email: String): User?
}
