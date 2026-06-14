package watson.resumaker

import watson.resumaker.network.AccountApi
import watson.resumaker.network.AccountApiImpl
import watson.resumaker.network.ApiClient
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.ExperienceApiImpl
import watson.resumaker.network.TargetApi
import watson.resumaker.network.TargetApiImpl
import watson.resumaker.network.createPlatformHttpClient
import watson.resumaker.session.SessionStore
import watson.resumaker.session.createSessionStore

/**
 * 앱 의존성 컨테이너(수동 DI). 세션·HttpClient·API들을 한 곳에서 조립한다.
 * ViewModel은 여기서 만든 API/Session만 의존한다(프레임워크 DI 없이 단순·예측 가능).
 *
 * @param baseUrl 백엔드 기본 URL(기본 localhost:8080).
 */
class AppContainer(
    baseUrl: String = ApiClient.DEFAULT_BASE_URL,
) {
    val session: SessionStore = createSessionStore()

    private val apiClient = ApiClient(
        baseUrl = baseUrl,
        session = session,
        engineHttpClient = createPlatformHttpClient(),
    )

    val accountApi: AccountApi = AccountApiImpl(apiClient)
    val experienceApi: ExperienceApi = ExperienceApiImpl(apiClient)
    val targetApi: TargetApi = TargetApiImpl(apiClient)
}
