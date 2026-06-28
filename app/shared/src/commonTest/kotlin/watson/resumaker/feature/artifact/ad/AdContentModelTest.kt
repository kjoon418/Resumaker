package watson.resumaker.feature.artifact.ad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AdContentModelTest {

    private val provider = SelfPromoAdContentProvider()

    @Test
    fun selectByJobIdIsDeterministic() {
        // 같은 작업 id에는 항상 같은 광고가 선택돼야 한다(폴링 중 깜빡임 방지). JVM/JS hashCode 차이로
        // 특정 버킷을 단언하지 않고 안정성(같은 입력 → 같은 출력)만 검증한다.
        val ids = listOf("job-1", "job-2", "job-3", "abc", "x", "한글-작업")
        for (id in ids) {
            val first = provider.pick(id)
            val second = provider.pick(id)
            assertSame(first, second, "같은 id($id)는 같은 광고를 반환해야 한다")
        }
    }

    @Test
    fun pickNeverReturnsNullForSelfPromoProvider() {
        // 자기 홍보 공급자는 항상 3종 중 하나를 돌려준다(graceful degradation은 외부 공급자 교체 시 적용).
        assertTrue(provider.pick("anything") != null)
    }

    @Test
    fun selectByJobIdHitsAllThreeBucketsAcrossInputs() {
        // % 3 분배가 0/1/2 버킷을 모두 탈 수 있는지(분배가 한 곳에 고정되지 않음). 특정 id→버킷 단언은
        // hashCode 플랫폼 차이로 피하고, 충분한 입력에서 세 destination이 모두 등장하는지만 본다.
        val destinations = (0..200).map { provider.pick("job-$it")!!.destination }.toSet()
        assertEquals(AdDestination.entries.toSet(), destinations)
    }

    @Test
    fun placeholderDestinationsMatchNavContract() {
        // P-1/P-2/P-3 → nav 계약 가드: 제목별 destination이 의도와 일치해야 한다(App.kt 배선 전제).
        val byDestination = (0..200).map { provider.pick("job-$it")!! }
            .associateBy { it.destination }

        assertEquals("경험을 더 강하게 다듬어 보세요", byDestination[AdDestination.EXPERIENCE_LIST]?.title)
        assertEquals("다양한 이력서 양식을 골라보세요", byDestination[AdDestination.TEMPLATE_LIST]?.title)
        assertEquals("AI가 지원 전략도 분석해 드려요", byDestination[AdDestination.TARGET_CREATE]?.title)
    }

    @Test
    fun contentDescriptionPrefixesAdLabel() {
        // 접근성: 스크린 리더가 광고임을 분명히 알 수 있도록 "광고:" 접두를 붙인다.
        val placeholder = provider.pick("job-1")!!
        assertEquals("광고: ${placeholder.title}", placeholder.contentDescription)
    }

    @Test
    fun adContentProviderInterfaceIsReplaceable() {
        // FR-1/AC 9.7 공급자 교체 seam: AdContentProvider 인터페이스 구현체를 교체해도
        // 슬롯·게이팅 코드 변경 없이 동작한다. 커스텀 공급자가 null을 반환(광고 없음)할 수 있어야 한다.
        val nullProvider = object : AdContentProvider {
            override fun pick(jobId: String): AdPlaceholder? = null
        }
        assertEquals(null, nullProvider.pick("any-id"))
        // 기본 공급자는 항상 non-null을 반환해 기존 계약을 유지한다.
        assertTrue(provider.pick("any-id") != null)
    }
}
