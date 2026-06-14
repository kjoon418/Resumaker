package watson.resumaker.account.domain

import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import watson.resumaker.common.domain.IdentifierGenerator

/**
 * 사용자 애그리거트 루트(구현 설계 §3.2).
 * 모든 경험 기록·목표 정보·산출물은 한 User에 귀속된다.
 *
 * 주생성자는 private, 신규 작성은 create(), DB 복원은 retrieve()로 분리한다(검증 가이드).
 */
@Entity
@Table(name = "users")
class User private constructor(
    @Id
    val id: UserId,
    @Embedded
    var credential: Credential,
    var timeZone: UserTimeZone,
) {

    companion object {
        /**
         * 신규 사용자를 만든다. 식별자를 도메인이 발급한다.
         */
        fun create(credential: Credential, timeZone: UserTimeZone): User =
            User(UserId(IdentifierGenerator.newId()), credential, timeZone)

        /**
         * 저장된 값으로 사용자를 복원한다.
         */
        fun retrieve(id: UserId, credential: Credential, timeZone: UserTimeZone): User =
            User(id, credential, timeZone)
    }
}
