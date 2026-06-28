package watson.resumaker.config

/**
 * 대기 시간 광고 슬롯 노출 여부(런타임 구성). 기본 true. 운영이 `window.__RESUMAKER_ADS_ENABLED__`를
 * `"false"`로 두면 끈다 — baseline 측정 기간·킬스위치용.
 */
expect fun adsEnabled(): Boolean
