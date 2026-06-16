package watson.resumaker.account.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserId
import watson.resumaker.account.infrastructure.PasswordHasher
import watson.resumaker.account.infrastructure.UserRepository
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.common.domain.UnauthorizedException
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.account.presentation.LoginResponse
import watson.resumaker.account.presentation.SignUpResponse
import watson.resumaker.artifact.infrastructure.ArtifactRepository
import watson.resumaker.target.infrastructure.TargetBriefRepository
import watson.resumaker.template.infrastructure.ResumeTemplateRepository

/**
 * 계정 유스케이스: 회원가입, 계정 삭제(귀속 데이터 cascade 삭제).
 *
 * 이메일 중복 검증은 DB에 의존하므로 서비스에서 수행한다(검증 가이드).
 * 로그인 검증도 영속된 비밀번호 해시에 의존하므로 서비스에서 수행하며, PasswordHasher를 주입받는다(검증 가이드).
 * 계정 삭제는 정합성 최우선(부분 삭제 금지)이므로 단일 트랜잭션으로 귀속 데이터를 함께 지운다(구현 설계 §3.2).
 * §2.5: 양식 삭제 정책은 목표 정보 정책을 따른다 — 계정 삭제 시 resumeTemplate도 함께 삭제한다.
 * 수용 기준 14: 계정 삭제 시 산출물(Artifact·버전·항목)도 같은 트랜잭션에서 함께 삭제한다.
 */
@Service
class AccountService(
    private val userRepository: UserRepository,
    private val experienceRecordRepository: ExperienceRecordRepository,
    private val targetBriefRepository: TargetBriefRepository,
    private val resumeTemplateRepository: ResumeTemplateRepository,
    private val artifactRepository: ArtifactRepository,
    private val mapper: AccountServiceMapper,
    private val passwordHasher: PasswordHasher,
) {

    /**
     * 타이밍 사이드채널 방지용 더미 해시(CQ-T1).
     * 이메일이 존재하지 않을 때도 PBKDF2 1회를 수행해 응답 시간으로 이메일 존재 여부가
     * 새지 않도록 한다. 빈 스트링을 포함한 유효 포맷 해시를 빈 스트링으로 1회 계산해 캐시한다.
     */
    private val dummyHash: String = passwordHasher.hash("")

    @Transactional
    fun signUp(command: SignUpCommand): SignUpResponse {
        validateEmailNotDuplicated(command.credential.email)

        val user = User.create(command.credential, command.timeZone)
        val savedUser = userRepository.save(user)
        return mapper.toSignUpResponse(savedUser)
    }

    /**
     * 이메일+비밀번호 로그인(구현 설계 §278). 성공 시 userId를 돌려준다(기존 X-User-Id 모델 유지).
     *
     * 계정 열거 방지를 위해 "이메일 미존재"와 "비밀번호 불일치"를 구분하지 않고
     * 동일한 일반 메시지로 인증 실패를 던진다(401).
     *
     * 타이밍 사이드채널 방지(CQ-T1): 이메일이 없을 때도 dummyHash로 matches()를 1회 실행해
     * 응답 시간이 이메일 존재 여부를 드러내지 않도록 한다.
     */
    @Transactional(readOnly = true)
    fun login(email: String, rawPassword: String): LoginResponse {
        val normalizedEmail = email.trim().lowercase()
        val user = userRepository.findByCredentialEmail(normalizedEmail)
        val hashToVerify = user?.credential?.passwordHash ?: dummyHash
        val passwordMatches = passwordHasher.matches(rawPassword, hashToVerify)
        if (user == null || !passwordMatches) {
            throw authenticationFailure()
        }
        return mapper.toLoginResponse(user)
    }

    @Transactional
    fun deleteAccount(userId: UserId) {
        validateUserExists(userId)

        experienceRecordRepository.deleteByOwnerId(userId)
        targetBriefRepository.deleteByOwnerId(userId)
        resumeTemplateRepository.deleteByOwnerId(userId)
        artifactRepository.deleteByOwnerId(userId)
        userRepository.deleteById(userId.value)
    }

    private fun validateEmailNotDuplicated(email: String) {
        if (userRepository.existsByCredentialEmail(email)) {
            throw DomainValidationException("이미 가입된 이메일이에요. 로그인하거나 다른 이메일을 사용해 주세요.")
        }
    }

    private fun validateUserExists(userId: UserId) {
        if (!userRepository.existsById(userId.value)) {
            throw DomainValidationException("이미 삭제되었거나 존재하지 않는 계정이에요.")
        }
    }

    /** 계정 열거 방지를 위해 이메일·비밀번호 어느 쪽이 틀렸는지 노출하지 않는 일반 인증 실패. */
    private fun authenticationFailure(): UnauthorizedException =
        UnauthorizedException("이메일 또는 비밀번호가 일치하지 않아요.")
}
