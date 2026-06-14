package watson.resumaker.network

import watson.resumaker.model.dto.ErrorResponse

/**
 * 네트워크 호출 결과를 도메인 친화적으로 표현하는 sealed 타입.
 * ViewModel은 예외 대신 이 타입으로 성공/실패를 분기한다(단방향 흐름·예측 가능성).
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    /**
     * 실패. [message]는 사용자에게 그대로 보여줄 한국어 안내(서버 ErrorResponse.message 우선).
     * [field]는 폼 인라인 에러 매핑용(없으면 null). [code]는 분기용 식별자.
     */
    data class Failure(
        val message: String,
        val code: String? = null,
        val field: String? = null,
    ) : ApiResult<Nothing>
}

/** 서버 에러 응답을 Failure로 변환. */
internal fun ErrorResponse.toFailure(): ApiResult.Failure =
    ApiResult.Failure(message = message, code = code, field = field)

/** 네트워크/직렬화 등 예기치 못한 실패의 기본 안내(막다른 길 금지 — 다시 시도 유도). */
internal const val DEFAULT_NETWORK_ERROR =
    "지금은 서버와 연결할 수 없어요. 잠시 후 다시 시도해 주세요."

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(value)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (ApiResult.Failure) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(this)
    return this
}
