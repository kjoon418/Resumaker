package watson.resumaker.common.domain

import java.util.UUID

/**
 * 도메인이 식별자를 발급할 때 사용하는 공통 유틸.
 * 식별자는 도메인이 생성 시점에 UUID로 발급한다(구현 설계 §3.1).
 */
object IdentifierGenerator {

    fun newId(): UUID = UUID.randomUUID()
}
