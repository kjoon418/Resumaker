package watson.resumaker.config

/** config.js가 설정한 전역에서 광고 슬롯 on/off를 읽는다. 명시적으로 "false"일 때만 false, 그 외(미설정 포함)는 true. */
private fun readAdsEnabled(): Boolean =
    js("(typeof window !== 'undefined' && window.__RESUMAKER_ADS_ENABLED__ === 'false') ? false : true")

actual fun adsEnabled(): Boolean = readAdsEnabled()
