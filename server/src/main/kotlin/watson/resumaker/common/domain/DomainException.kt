package watson.resumaker.common.domain

/**
 * 도메인 계층에서 발생하는 예외의 최상위 타입.
 * 글로벌 핸들러(presentation)가 이 계층을 HTTP 응답으로 변환한다.
 */
sealed class DomainException(message: String) : RuntimeException(message)

/**
 * 도메인 불변식 위반(VO/엔티티 검증 실패). HTTP 400으로 매핑된다.
 */
class DomainValidationException(message: String) : DomainException(message)

/**
 * 리소스를 찾을 수 없거나, 소유자가 아닌 사용자가 접근한 경우.
 * 존재 노출을 최소화하기 위해 권한 위반도 이 예외로 통합해 HTTP 404로 매핑한다.
 */
class ResourceNotFoundException(message: String) : DomainException(message)

/**
 * 인증 주체를 해석할 수 없는 경우. HTTP 401로 매핑된다.
 */
class UnauthorizedException(message: String) : DomainException(message)
