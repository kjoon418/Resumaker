package watson.resumaker.account.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.resumaker.account.domain.User
import watson.resumaker.account.domain.UserId
import watson.resumaker.account.infrastructure.UserRepository
import watson.resumaker.common.domain.DomainValidationException
import watson.resumaker.experience.infrastructure.ExperienceRecordRepository
import watson.resumaker.account.presentation.SignUpResponse
import watson.resumaker.target.infrastructure.TargetBriefRepository

/**
 * 계정 유스케이스: 회원가입, 계정 삭제(귀속 데이터 cascade 삭제).
 *
 * 이메일 중복 검증은 DB에 의존하므로 서비스에서 수행한다(검증 가이드).
 * 계정 삭제는 정합성 최우선(부분 삭제 금지)이므로 단일 트랜잭션으로 귀속 데이터를 함께 지운다(구현 설계 §3.2).
 */
@Service
class AccountService(
    private val userRepository: UserRepository,
    private val experienceRecordRepository: ExperienceRecordRepository,
    private val targetBriefRepository: TargetBriefRepository,
    private val mapper: AccountServiceMapper,
) {

    @Transactional
    fun signUp(command: SignUpCommand): SignUpResponse {
        validateEmailNotDuplicated(command.credential.email)

        val user = User.create(command.credential, command.timeZone)
        val savedUser = userRepository.save(user)
        return mapper.toSignUpResponse(savedUser)
    }

    @Transactional
    fun deleteAccount(userId: UserId) {
        validateUserExists(userId)

        experienceRecordRepository.deleteByOwnerId(userId)
        targetBriefRepository.deleteByOwnerId(userId)
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
}
