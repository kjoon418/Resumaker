package watson.resumaker.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 단순 스택 기반 내비게이터. 현재 화면과 뒤로가기 스택을 보관한다(단방향: 화면은 이벤트만 호출).
 *
 * 탭/루트 전환은 스택을 리셋(루트 교체)하고, 상세 진입은 push, 뒤로가기는 pop한다.
 *
 * WX-8: [history]가 주어지면 화면 전환을 브라우저 URL과 동기화한다(push→pushState, switchRoot→replaceState).
 * 브라우저 back/forward(popstate)는 [start]에서 등록한 리스너가 [syncFromPath]로 반영한다.
 * 테스트/비웹은 history=null로 두면 URL 동기화 없이 인메모리로만 동작한다.
 *
 * WX-4: 저장 후 목록 복귀 시 목록 상단 스낵바를 1회 노출하기 위한 [pendingMessage]를 둔다.
 * 목록 화면이 진입 시 [consumePendingMessage]로 1회 읽는다.
 */
@Stable
class AppNavigator(
    start: Screen,
    private val history: BrowserHistory? = null,
) {
    private val backStack = ArrayDeque<Screen>().apply { addLast(start) }

    var current by mutableStateOf(start)
        private set

    /** 저장 후 목록 복귀 등에서 목록이 1회 읽을 성공 메시지(WX-4/16). */
    var pendingMessage by mutableStateOf<String?>(null)
        private set

    val canGoBack: Boolean get() = backStack.size > 1

    init {
        history?.let { h ->
            // 딥링크/새로고침: 초기 경로가 start와 다르면 현재 항목을 그 화면으로 교체.
            h.replace(Routes.pathOf(start))
            h.onPopState { path -> syncFromPath(path) }
        }
    }

    /** 상세/하위 화면으로 진입(스택 push). URL은 pushState. */
    fun push(screen: Screen) {
        backStack.addLast(screen)
        current = screen
        history?.push(Routes.pathOf(screen))
    }

    /**
     * 현재 화면을 [screen]으로 원자적으로 교체한다(스택 깊이 유지). URL은 replaceState.
     *
     * L-3: 프리셋 선택·확정·폴백 내비처럼 "한 화면을 닫고 다른 화면을 여는" 전환에서
     * pop()→push() 이중 호출은 브라우저 history에 중간 URL을 한 단계 더 쌓는다.
     * replaceTop은 top 항목만 바꾸고 replaceState로 동기화해 history 누적을 한 단계로 만든다.
     */
    fun replaceTop(screen: Screen) {
        backStack.removeLast()
        backStack.addLast(screen)
        current = screen
        history?.replace(Routes.pathOf(screen))
    }

    /** 탭/루트 전환: 스택을 단일 루트로 리셋. URL은 replaceState(히스토리 누적 방지). */
    fun switchRoot(screen: Screen) {
        backStack.clear()
        backStack.addLast(screen)
        current = screen
        history?.replace(Routes.pathOf(screen))
    }

    /**
     * 뒤로가기(스택 pop). 루트면 논리적 상위([parentOf])로 이동한다(#6 fallback).
     *
     * CQ-1: history가 있을 때는 window.history.back()에 위임한다. 브라우저가 비동기로 popstate를
     * 발화하면 [syncFromPath]가 스택을 갱신하므로 여기서 직접 pop 하지 않는다(이중 갱신 방지).
     * 단, 브라우저 히스토리에 이전 항목이 없는 경우(직접 진입·새로고침)에도 back()을 위임하면
     * 앱 밖으로 나가거나 아무 반응이 없다. 이를 막기 위해 스택이 1개(루트)일 때는 직접 fallback 처리한다.
     * history가 없을 때(테스트·인메모리)도 직접 스택 pop 또는 fallback.
     */
    fun pop() {
        if (history != null) {
            if (backStack.size > 1) {
                history.back()
                // 스택 갱신은 popstate → syncFromPath 경로에서 처리.
            } else {
                // 브라우저 히스토리 없음(직접 진입): 화면별 논리적 상위로 이동(#6).
                navigateToParent()
            }
        } else {
            if (backStack.size > 1) {
                backStack.removeLast()
                current = backStack.last()
            } else {
                navigateToParent()
            }
        }
    }

    /**
     * 히스토리가 없어 뒤로가기가 불가한 경우, 화면별 논리적 상위로 이동한다(#6 fallback).
     * 상위가 없는 루트 화면(Home, Session 등)은 무시한다.
     */
    private fun navigateToParent() {
        val parent = parentOf(current) ?: return
        backStack.clear()
        backStack.addLast(parent)
        current = parent
        history?.replace(Routes.pathOf(parent))
    }

    companion object {
        /**
         * 화면별 논리적 상위 화면 매핑(#6). 주소 직접 진입·새로고침 후 뒤로가기 fallback에 사용한다.
         * - 편집 화면 → 해당 목록
         * - 산출물 열람/버전 → 홈(목록이 없는 경우)
         * - 루트 화면(Home, Session, 목록 등) → null(뒤로가기 없음)
         */
        fun parentOf(screen: Screen): Screen? = when (screen) {
            is Screen.ExperienceEdit -> Screen.ExperienceList
            is Screen.TargetDetail -> Screen.TargetList
            // 기존 목표 수정의 상위는 상세, 신규 생성의 상위는 목록.
            is Screen.TargetEdit -> if (screen.targetId != null) Screen.TargetDetail(screen.targetId) else Screen.TargetList
            is Screen.TemplateEdit -> Screen.TemplateList
            Screen.TemplatePreset -> Screen.TemplateList
            Screen.TemplateInterpret -> Screen.TemplateList
            is Screen.Artifact -> Screen.Home
            Screen.ArtifactList -> Screen.Home
            // 산출물은 목록을 거쳐 열람하므로 열람의 상위는 목록(기존 Home에서 변경).
            is Screen.ArtifactView -> Screen.ArtifactList
            is Screen.ArtifactVersions -> Screen.ArtifactView(screen.artifactId)
            else -> null
        }
    }

    /**
     * WX-4: 편집 화면에서 "해당 도메인 목록"으로 복귀하며 성공 메시지를 남긴다.
     * 스택에 이미 목록이 있으면 그 위를 정리해 목록을 노출하고, 없으면 목록을 루트로 세운다.
     */
    fun returnToList(list: Screen, message: String) {
        pendingMessage = message
        // 스택에서 목록을 찾아 그 지점까지 되감기(편집·신규 진입 경로 모두 목록으로 수렴).
        val index = backStack.indexOfLast { it == list }
        if (index >= 0) {
            while (backStack.size > index + 1) backStack.removeLast()
            current = backStack.last()
        } else {
            backStack.clear()
            backStack.addLast(list)
            current = list
        }
        history?.push(Routes.pathOf(current))
    }

    /** 목록이 진입 시 1회 읽고 클리어(중복 노출 방지). */
    fun consumePendingMessage(): String? {
        val m = pendingMessage
        pendingMessage = null
        return m
    }

    /** 브라우저 back/forward(popstate)로 들어온 경로를 현재 화면에 반영. 알 수 없으면 무시. */
    fun syncFromPath(path: String) {
        val screen = Routes.screenOf(path) ?: return
        current = screen
        // 스택도 일관되게 정리: 이미 있으면 그 지점, 없으면 새 루트.
        val index = backStack.indexOfLast { it == screen }
        if (index >= 0) {
            while (backStack.size > index + 1) backStack.removeLast()
        } else {
            backStack.addLast(screen)
        }
    }

    /** 인증 진입/만료 시 세션 화면으로 완전 리셋. */
    fun resetToSession() = switchRoot(Screen.Session)
}
