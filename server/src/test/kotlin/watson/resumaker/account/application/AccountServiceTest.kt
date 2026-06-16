package watson.resumaker.account.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import watson.resumaker.account.domain.Credential
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserTimeZone
import watson.resumaker.account.infrastructure.PasswordHasher
import watson.resumaker.account.infrastructure.UserRepository
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.common.domain.UnauthorizedException
import watson.resumaker.account.domain.UserId
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.target.infrastructure.TargetBriefRepository
import watson.resumaker.template.infrastructure.ResumeTemplateRepository
import java.util.UUID

class AccountServiceTest {

    private val userRepository: UserRepository = mock()
    private val experienceRecordRepository: ExperienceRecordRepository = mock()
    private val targetBriefRepository: TargetBriefRepository = mock()
    private val resumeTemplateRepository: ResumeTemplateRepository = mock()
    private val artifactRepository: ArtifactRepository = mock()
    private val passwordHasher: PasswordHasher = mock()
    private val mapper = AccountServiceMapper()

    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        // AccountService.init이 passwordHasher.hash("")로 dummyHash를 계산하므로,
        // 생성 전에 유효 포맷 해시를 반환하도록 스텁한다(CQ-T1 타이밍 동일화 설계).
        whenever(passwordHasher.hash(any())).thenReturn(DUMMY_HASH_STUB)
        accountService = AccountService(
            userRepository = userRepository,
            experienceRecordRepository = experienceRecordRepository,
            targetBriefRepository = targetBriefRepository,
            resumeTemplateRepository = resumeTemplateRepository,
            artifactRepository = artifactRepository,
            mapper = mapper,
            passwordHasher = passwordHasher,
        )
    }

    @Nested
    inner class 로그인 {

        @Test
        fun 이메일과_비밀번호가_일치하면_userId를_반환한다() {
            // given
            val storedUser = User.create(Credential.of(EMAIL, STORED_HASH), UserTimeZone.DEFAULT)
            whenever(userRepository.findByCredentialEmail(EMAIL)).thenReturn(storedUser)
            whenever(passwordHasher.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(true)

            // when
            val response = accountService.login(EMAIL, RAW_PASSWORD)

            // then
            assertThat(response.userId).isEqualTo(storedUser.id.value.toString())
        }

        @Test
        fun 비밀번호가_일치하지_않으면_인증_실패_예외를_던진다() {
            // given
            val storedUser = User.create(Credential.of(EMAIL, STORED_HASH), UserTimeZone.DEFAULT)
            whenever(userRepository.findByCredentialEmail(EMAIL)).thenReturn(storedUser)
            whenever(passwordHasher.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(false)

            // when and then
            assertThatThrownBy { accountService.login(EMAIL, RAW_PASSWORD) }
                .isInstanceOf(UnauthorizedException::class.java)
                .hasMessage(AUTH_FAILURE_MESSAGE)
        }

        @Test
        fun 존재하지_않는_이메일이면_비밀번호_불일치와_동일한_인증_실패_예외를_던진다() {
            // given
            // 타이밍 사이드채널 방지(CQ-T1): 미존재 시에도 dummyHash로 matches()가 호출된다.
            whenever(userRepository.findByCredentialEmail(EMAIL)).thenReturn(null)
            whenever(passwordHasher.matches(any(), any())).thenReturn(false)

            // when and then
            assertThatThrownBy { accountService.login(EMAIL, RAW_PASSWORD) }
                .isInstanceOf(UnauthorizedException::class.java)
                .hasMessage(AUTH_FAILURE_MESSAGE)

            // 미존재 경로에서도 matches()가 반드시 1회 호출돼야 한다(타이밍 동일화).
            verify(passwordHasher).matches(any(), any())
        }

        @Test
        fun 이메일_앞뒤_공백을_제거하고_소문자로_정규화한_뒤_조회한다() {
            // given
            val storedUser = User.create(Credential.of(EMAIL, STORED_HASH), UserTimeZone.DEFAULT)
            whenever(userRepository.findByCredentialEmail(EMAIL)).thenReturn(storedUser)
            whenever(passwordHasher.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(true)

            // when
            accountService.login("  ${EMAIL.uppercase()}  ", RAW_PASSWORD)

            // then — lowercase + trim 후 조회
            verify(userRepository).findByCredentialEmail(EMAIL)
        }

        @Test
        fun 이메일_대소문자가_달라도_로그인에_성공한다() {
            // given
            val storedUser = User.create(Credential.of(EMAIL, STORED_HASH), UserTimeZone.DEFAULT)
            whenever(userRepository.findByCredentialEmail(EMAIL)).thenReturn(storedUser)
            whenever(passwordHasher.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(true)

            // when
            val response = accountService.login(EMAIL_MIXED_CASE, RAW_PASSWORD)

            // then
            assertThat(response.userId).isEqualTo(storedUser.id.value.toString())
            verify(userRepository).findByCredentialEmail(EMAIL)
        }
    }

    @Nested
    inner class 계정삭제 {

        @Test
        fun `계정 삭제 시 경험·목표·양식·산출물이 모두 같은 트랜잭션에서 삭제된다`() {
            // given
            val userId = UserId(UUID.randomUUID())
            whenever(userRepository.existsById(userId.value)).thenReturn(true)

            // when
            accountService.deleteAccount(userId)

            // then — 네 귀속 데이터 레포 모두 deleteByOwnerId가 호출돼야 한다(회귀 방지, 수용 기준 14).
            verify(experienceRecordRepository).deleteByOwnerId(userId)
            verify(targetBriefRepository).deleteByOwnerId(userId)
            verify(resumeTemplateRepository).deleteByOwnerId(userId)
            verify(artifactRepository).deleteByOwnerId(userId)
            verify(userRepository).deleteById(userId.value)
        }
    }

    companion object {
        private const val EMAIL = "user@example.com"
        private const val EMAIL_MIXED_CASE = "User@Example.COM"
        private const val RAW_PASSWORD = "password1"
        private const val STORED_HASH = "120000:salt:hash"
        private const val DUMMY_HASH_STUB = "120000:dummySalt:dummyHash"
        private const val AUTH_FAILURE_MESSAGE = "이메일 또는 비밀번호가 일치하지 않아요."
    }
}
