package watson.resumaker.navigation

/**
 * 브라우저 History API 추상화(WX-8). 플랫폼별 expect/actual.
 * - [currentPath]: 현재 location.pathname(딥링크·새로고침 초기 경로 복원용).
 * - [push]: pushState로 주소만 갱신(화면 전환 시).
 * - [replace]: replaceState로 현재 항목 교체(루트 전환 시 히스토리 누적 방지).
 * - [back]: window.history.back()으로 브라우저 히스토리를 한 칸 뒤로(인앱 pop 위임, CQ-1).
 * - [onPopState]: 브라우저 back/forward 시 경로 콜백 등록.
 *
 * 테스트/비웹 플랫폼은 no-op 구현으로 두어 공통 코드가 깨지지 않게 한다.
 */
expect class BrowserHistory() {
    fun currentPath(): String
    fun push(path: String)
    fun replace(path: String)
    fun back()
    fun onPopState(listener: (String) -> Unit)
}
