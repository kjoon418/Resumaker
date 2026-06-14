package watson.resumaker.session

/**
 * 세션(현재 사용자) 보관소. 로그인 엔드포인트가 없으므로(브리프 §API),
 * 가입/재진입으로 확보한 userId를 보관해 이후 `X-User-Id` 헤더로 사용한다.
 *
 * 영속 구현(웹 localStorage 등)은 플랫폼별 expect/actual로 제공한다([createSessionStore]).
 */
interface SessionStore {
    /** 현재 보관된 userId(없으면 null). */
    fun currentUserId(): String?

    /** 현재 보관된 이메일(가입 시 입력값, 마이페이지 표시용; 없으면 null). */
    fun currentEmail(): String?

    /** 세션 저장(가입/재진입 성공 시). email은 재진입 시 모를 수 있어 선택값. */
    fun save(userId: String, email: String?)

    /** 세션 클리어(로그아웃·탈퇴). */
    fun clear()
}

/** 플랫폼별 SessionStore 생성(웹: localStorage 기반). */
expect fun createSessionStore(): SessionStore
