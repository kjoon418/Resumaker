package watson.resumaker

import watson.resumaker.network.AccountApi
import watson.resumaker.network.AccountApiImpl
import watson.resumaker.network.ApiClient
import watson.resumaker.network.ArtifactApi
import watson.resumaker.network.ArtifactApiImpl
import watson.resumaker.network.ExperienceApi
import watson.resumaker.network.ExperienceApiImpl
import watson.resumaker.network.TargetApi
import watson.resumaker.network.TargetApiImpl
import watson.resumaker.network.TemplateApi
import watson.resumaker.network.TemplateApiImpl
import watson.resumaker.network.TemplateInterpretApi
import watson.resumaker.network.TemplateInterpretApiImpl
import watson.resumaker.network.TemplatePresetApi
import watson.resumaker.network.TemplatePresetApiImpl
import watson.resumaker.network.configuredApiBaseUrl
import watson.resumaker.network.createPlatformHttpClient
import watson.resumaker.session.SessionStore
import watson.resumaker.session.createSessionStore

/**
 * 앱 의존성 컨테이너(수동 DI). 세션·HttpClient·API들을 한 곳에서 조립한다.
 * ViewModel은 여기서 만든 API/Session만 의존한다(프레임워크 DI 없이 단순·예측 가능).
 *
 * @param baseUrl 백엔드 기본 URL. 배포 시 주입된 런타임 구성([configuredApiBaseUrl])을 우선 쓰고, 없으면 기본값(localhost:8082).
 */
class AppContainer(
    baseUrl: String = configuredApiBaseUrl() ?: ApiClient.DEFAULT_BASE_URL,
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
    val templateApi: TemplateApi = TemplateApiImpl(apiClient)
    val templatePresetApi: TemplatePresetApi = TemplatePresetApiImpl(apiClient)
    val templateInterpretApi: TemplateInterpretApi = TemplateInterpretApiImpl(apiClient)
    val artifactApi: ArtifactApi = ArtifactApiImpl(apiClient)
}
