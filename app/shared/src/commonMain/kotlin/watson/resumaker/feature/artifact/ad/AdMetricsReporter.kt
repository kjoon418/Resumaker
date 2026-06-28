package watson.resumaker.feature.artifact.ad

/**
 * 광고 계측 리포터 seam. 노출(impression)과 CTA 클릭(click) 이벤트를 기록한다.
 * 기본 구현은 no-op이므로 구현하지 않아도 계측이 묵음 처리된다.
 * 실제 분석 백엔드 연결 시 이 인터페이스를 구현해 교체한다(docs/광고-운영-핸드오프.md 참고).
 */
interface AdMetricsReporter {
    fun onImpression(placeholder: AdPlaceholder) {}
    fun onClick(placeholder: AdPlaceholder) {}
}

/**
 * 개발/QA용 임시 싱크. 노출·클릭 이벤트를 콘솔에 출력해 계측 흐름을 육안으로 확인할 수 있게 한다.
 * 실제 분석 백엔드 연결 시 이 인터페이스를 구현해 교체한다(docs/광고-운영-핸드오프.md 참고).
 */
class ConsoleAdMetricsReporter : AdMetricsReporter {
    override fun onImpression(placeholder: AdPlaceholder) {
        println("[ad] impression: ${placeholder.title} -> ${placeholder.destination}")
    }

    override fun onClick(placeholder: AdPlaceholder) {
        println("[ad] click: ${placeholder.title} -> ${placeholder.destination}")
    }
}
