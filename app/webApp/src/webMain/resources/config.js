// 런타임 API base URL 구성. 로컬 dev 기본값이며, 배포(nginx 컨테이너)는 API_BASE 환경변수로 이 파일을 덮어쓴다.
// index.html에서 webApp.js보다 먼저 로드되어 앱이 읽는다(watson.resumaker.network.configuredApiBaseUrl).
window.__RESUMAKER_API_BASE__ = "http://localhost:8082";

// 대기 시간 광고 슬롯 on/off. baseline 측정 기간엔 "false"로 둔다. 배포 시 nginx가 ADS_ENABLED로 덮어쓴다.
window.__RESUMAKER_ADS_ENABLED__ = "true";
