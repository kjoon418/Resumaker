package watson.resumaker.account.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import watson.resumaker.account.domain.Credential
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserTimeZone

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private lateinit var repository: UserRepository

    @Test
    fun 사용자를_저장하고_식별자로_복원한다() {
        // given
        val user = User.create(
            credential = Credential.of("user@example.com", "hashed"),
            timeZone = UserTimeZone("Asia/Seoul"),
        )

        // when
        val saved = repository.saveAndFlush(user)
        val found = repository.findById(saved.id.value).orElse(null)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.credential.email).isEqualTo("user@example.com")
        assertThat(found.timeZone.value).isEqualTo("Asia/Seoul")
    }

    @Test
    fun 이메일_중복_여부를_확인한다() {
        // given
        repository.saveAndFlush(
            User.create(Credential.of("dup@example.com", "hashed"), UserTimeZone.DEFAULT),
        )

        // when and then
        assertThat(repository.existsByCredentialEmail("dup@example.com")).isTrue()
        assertThat(repository.existsByCredentialEmail("fresh@example.com")).isFalse()
    }
}
