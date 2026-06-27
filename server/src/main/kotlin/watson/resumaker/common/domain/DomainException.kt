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

/**
 * 빈 경험 묶음으로 산출물 생성을 시도한 경우(수용 기준 8, 구현 설계 §9).
 * 입력 형식 오류가 아니라 "지금 상태로는 생성 불가"이므로 409로 매핑하고, 경험 추가를 유도하는
 * action을 함께 내려 사용자가 막다른 길에 빠지지 않게 한다.
 */
class EmptyExperienceSelectionException(message: String) : DomainException(message)

/**
 * 현재 상태와 충돌해 요청을 진행할 수 없는 경우. HTTP 409로 매핑된다.
 * 예: 같은 생성 항목에 대한 재생성이 이미 진행 중일 때의 중복 요청(수용 기준 20, 구현 설계 §185·§305).
 * 입력 형식 오류가 아니라 "지금은 그 작업을 할 수 없다"이므로 409이며, 사용자가 취할 행동 힌트를 action에 담을 수 있다.
 */
class ConflictException(
    message: String,
    val action: String? = null,
) : DomainException(message)

/**
 * 비용 가드레일 상한 초과로 작업을 진행할 수 없는 경우(도메인 이해 §396~399·404, 수용 기준 15).
 *
 * 1차 생성 한도(사용자당·하루)나 항목 재생성 한도(생성 항목당·하루)에 도달하면 작업을 막는다. 입력 오류도,
 * 상태 충돌도 아니라 "사용량 한도 소진"이므로 **HTTP 429(Too Many Requests)**로 매핑한다(의미상 정확 —
 * 429는 '요청 자체는 유효하나 사용량 한도를 초과'를 뜻함). 사용자에게 회복 시점(시간대 기준 내일 자정)과
 * 대안(직접 편집 등)을 message·action으로 안내한다(§399, UX 에러 가이드).
 *
 * @param code   클라이언트 분기용 식별자(예: GENERATION_QUOTA_EXCEEDED, REGENERATION_QUOTA_EXCEEDED).
 * @param action 사용자가 취할 수 있는 대안 행동 힌트(예: EDIT_MANUALLY). 없으면 null.
 */
class QuotaExceededException(
    message: String,
    val code: String,
    val action: String? = null,
) : DomainException(message)

/**
 * 외부 AI 생성이 일시적으로 산출물(또는 재생성 항목)을 만들지 못한 경우(B4). 인프라 예외
 * ([watson.resumaker.generation.infrastructure.ClaudeCliException])와 달리 "재생성 실패"·"전 항목 일시 실패" 같은
 * **도메인 의미를 메시지로 보존**하되, 사용자가 고칠 입력이 없는 일시적 불가이므로 HTTP 503(RETRY_LATER)로 매핑한다.
 *
 * 입력성 거부(근거 0 등 → 400/EDIT_INPUTS)와 의미를 분리한다. 인프라 예외를 재사용하면 핸들러가 메시지를 고정 문구
 * ("AI 생성을 사용할 수 없어요")로 덮어 "재생성 실패" 의미가 흐려지므로, 도메인 예외로 분리해 문구를 보존한다.
 *
 * @param action 사용자가 취할 수 있는 행동 힌트(기본 RETRY_LATER — 잠시 후 다시 시도).
 */
class GenerationUnavailableException(
    message: String,
    val action: String? = "RETRY_LATER",
) : DomainException(message)
