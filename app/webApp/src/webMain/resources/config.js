// 런타임 API base URL 구성. 로컬 dev 기본값이며, 배포(nginx 컨테이너)는 API_BASE 환경변수로 이 파일을 덮어쓴다.
// index.html에서 webApp.js보다 먼저 로드되어 앱이 읽는다(watson.resumaker.network.configuredApiBaseUrl).
window.__RESUMAKER_API_BASE__ = "http://localhost:8082";

// 대기 시간 광고 슬롯 on/off. baseline 측정 기간엔 "false"로 둔다. 배포 시 nginx가 ADS_ENABLED로 덮어쓴다.
window.__RESUMAKER_ADS_ENABLED__ = "true";

// AdSense 퍼블리셔 ID(ca-pub-…). 로컬 dev 기본값은 빈 값(무동작). 배포 시 nginx가 ADSENSE_CLIENT로 덮어쓴다.
// 실제 AdSense 광고는 공개 콘텐츠 표면에 싣는다(docs/광고-AdSense-전략.md) — 이 값은 그 표면이 읽는 런타임 주입점.
window.__RESUMAKER_ADSENSE_CLIENT__ = "";
