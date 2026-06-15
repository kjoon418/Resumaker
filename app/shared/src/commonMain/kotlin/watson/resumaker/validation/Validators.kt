package watson.resumaker.validation

/**
 * commonMain 공통 검증. 클라이언트 즉시 피드백용이며, 서버 도메인 검증과 규칙을 일치시킨다(KMP 가이드 §3).
 * 각 함수는 통과 시 null, 실패 시 사용자 안내 메시지를 반환한다(UX 에러 가이드 톤).
 */
object Validators {

    /** RFC 단순화 이메일 패턴(로컬@도메인.tld). 과도하게 엄격하지 않게 둔다. */
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    const val MIN_PASSWORD_LENGTH = 8

    /** 이메일 형식 검증. */
    fun validateEmail(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "이메일을 입력해 주세요."
        if (!emailRegex.matches(trimmed)) return "이메일 형식을 다시 확인해 주세요. 예: name@example.com"
        return null
    }

    /** 비밀번호 8자 이상. */
    fun validatePassword(value: String): String? {
        if (value.isEmpty()) return "비밀번호를 입력해 주세요."
        if (value.length < MIN_PASSWORD_LENGTH) return "비밀번호는 8자 이상으로 입력해 주세요."
        return null
    }

    /** 복구 코드(UUID) 형식 검증 — 재진입용. 변수·함수명 userId는 내부 식별자로 유지(WX-2). */
    fun validateUserId(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "발급받은 복구 코드를 입력해 주세요."
        if (!uuidRegex.matches(trimmed)) return "복구 코드 형식이 올바르지 않아요. 가입할 때 받은 값을 그대로 붙여넣어 주세요."
        return null
    }

    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** 필수 텍스트(공백 제외) 검증. [missingMessage]는 비었을 때 안내. */
    fun validateRequired(value: String, missingMessage: String): String? {
        if (value.trim().isEmpty()) return missingMessage
        return null
    }

    /**
     * UX-7: 비밀번호 helper 실시간 피드백. 에러가 표시 중이면 null(에러 메시지가 우선),
     * 8자 도달 시 긍정 전환, 미만이면 안내 문구.
     */
    fun passwordHelper(password: String, hasError: Boolean): String? {
        if (hasError) return null
        return if (password.length >= MIN_PASSWORD_LENGTH) {
            "사용할 수 있는 비밀번호예요."
        } else {
            "8자 이상으로 입력해 주세요."
        }
    }
}
