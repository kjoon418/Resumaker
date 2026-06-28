package watson.resumaker.feature.artifact.ad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdMetricsReporterTest {

    // 기존 AdContentModelTest와 동일하게 공급자를 통해 플레이스홀더를 얻는다(RmIcons 직접 참조 회피).
    private val placeholder: AdPlaceholder = SelfPromoAdContentProvider().pick("test-job")!!

    @Test
    fun defaultInterfaceMethodsAreNoOp() {
        // 인터페이스 기본 메서드는 예외 없이 아무것도 하지 않는다.
        val reporter = object : AdMetricsReporter {}
        reporter.onImpression(placeholder)
        reporter.onClick(placeholder)
        // 예외가 발생하지 않으면 통과
    }

    @Test
    fun fakeReporterRecordsImpressions() {
        val recorded = mutableListOf<AdPlaceholder>()
        val reporter = object : AdMetricsReporter {
            override fun onImpression(p: AdPlaceholder) { recorded += p }
        }

        reporter.onImpression(placeholder)

        assertEquals(1, recorded.size)
        assertEquals(placeholder, recorded[0])
    }

    @Test
    fun fakeReporterRecordsClicks() {
        val recorded = mutableListOf<AdPlaceholder>()
        val reporter = object : AdMetricsReporter {
            override fun onClick(p: AdPlaceholder) { recorded += p }
        }

        reporter.onClick(placeholder)

        assertEquals(1, recorded.size)
        assertEquals(placeholder, recorded[0])
    }

    @Test
    fun fakeReporterRecordsMultipleEvents() {
        val impressions = mutableListOf<AdPlaceholder>()
        val clicks = mutableListOf<AdPlaceholder>()
        val reporter = object : AdMetricsReporter {
            override fun onImpression(p: AdPlaceholder) { impressions += p }
            override fun onClick(p: AdPlaceholder) { clicks += p }
        }

        reporter.onImpression(placeholder)
        reporter.onImpression(placeholder)
        reporter.onClick(placeholder)

        assertEquals(2, impressions.size)
        assertEquals(1, clicks.size)
    }

    @Test
    fun consoleReporterDoesNotThrowOnImpression() {
        // ConsoleAdMetricsReporter가 예외 없이 동작하는지 확인.
        ConsoleAdMetricsReporter().onImpression(placeholder)
    }

    @Test
    fun consoleReporterDoesNotThrowOnClick() {
        ConsoleAdMetricsReporter().onClick(placeholder)
    }

    @Test
    fun reporterIsReplaceable() {
        // 교체 가능한 seam: 다른 구현체로 바꿔도 호출 계약이 유지된다.
        var impressionCalled = false
        var clickCalled = false
        val reporter: AdMetricsReporter = object : AdMetricsReporter {
            override fun onImpression(p: AdPlaceholder) { impressionCalled = true }
            override fun onClick(p: AdPlaceholder) { clickCalled = true }
        }

        assertFalse(impressionCalled)
        assertFalse(clickCalled)

        reporter.onImpression(placeholder)
        assertTrue(impressionCalled)
        assertFalse(clickCalled)

        reporter.onClick(placeholder)
        assertTrue(clickCalled)
    }
}
