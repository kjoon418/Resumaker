package watson.resumaker.network

/**
 * 배포 시 주입되는 백엔드 API base URL. 없으면 null을 반환하고 호출부가 [ApiClient.DEFAULT_BASE_URL]로 폴백한다.
 *
 * 다른 도메인(cross-site)으로 배포하면 프런트는 백엔드 주소를 알아야 한다. wasm에 주소가 컴파일-인되므로 환경마다
 * 재빌드하지 않도록 **런타임 구성**으로 푼다: 웹은 nginx가 환경변수로 굽는 `config.js`의 전역
 * `window.__RESUMAKER_API_BASE__`에서 읽는다(로컬 dev는 번들된 기본값=localhost:8082).
 */
expect fun configuredApiBaseUrl(): String?
