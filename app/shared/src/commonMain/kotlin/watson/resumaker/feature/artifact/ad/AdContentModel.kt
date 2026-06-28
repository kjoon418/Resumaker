package watson.resumaker.feature.artifact.ad

import androidx.compose.ui.graphics.vector.ImageVector
import watson.resumaker.ui.theme.RmIcons
import kotlin.math.absoluteValue

/**
 * 대기 시간 광고 슬롯이 이동시킬 수 있는 앱 내 목적지. 외부 광고가 아닌 자기 홍보(self-promo)
 * 플레이스홀더이므로, 클릭 시 사용자가 기다리는 동안 가치 있는 다음 행동(경험 점검·양식 둘러보기·목표
 * 추가)으로 안내한다. 화면(App.kt)이 이 값을 실제 네비게이션으로 배선한다.
 */
enum class AdDestination { EXPERIENCE_LIST, TEMPLATE_LIST, TARGET_CREATE }

/**
 * 광고 슬롯에 렌더할 한 건의 플레이스홀더 콘텐츠. 외부 광고 SDK 연동 전까지 자기 홍보 콘텐츠로 채운다.
 * [contentDescription]은 스크린 리더가 광고임을 분명히 알 수 있도록 "광고:" 접두를 붙인다(광고 명시 라벨).
 */
data class AdPlaceholder(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val ctaText: String,
    val destination: AdDestination,
) {
    val contentDescription get() = "광고: $title"
}

/**
 * 광고 콘텐츠 공급 seam. 외부 광고 네트워크로 교체 가능하도록 인터페이스로 분리한다.
 * [pick]이 null을 반환하면 슬롯을 렌더하지 않는다(graceful degradation — 광고가 없으면 조용히 사라짐).
 */
interface AdContentProvider {
    fun pick(jobId: String): AdPlaceholder?
}

/**
 * MVP 자기 홍보 공급자. 고정된 3종 플레이스홀더 중 작업 id로 결정적으로 하나를 고른다([selectByJobId]).
 * 같은 작업에는 폴링 동안 같은 광고가 유지돼(깜빡임 방지) 시각적 안정성을 준다.
 */
class SelfPromoAdContentProvider : AdContentProvider {

    private val placeholders: List<AdPlaceholder> = listOf(
        AdPlaceholder(
            icon = RmIcons.CheckCircle,
            title = "경험을 더 강하게 다듬어 보세요",
            description = "모호한 수치나 빠진 내용을 점검해 이력서 완성도를 높여요.",
            ctaText = "경험 점검하러 가기 →",
            destination = AdDestination.EXPERIENCE_LIST,
        ),
        AdPlaceholder(
            icon = RmIcons.Note,
            title = "다양한 이력서 양식을 골라보세요",
            description = "신입부터 경력자까지, 목표에 맞는 양식이 준비되어 있어요.",
            ctaText = "양식 둘러보기 →",
            destination = AdDestination.TEMPLATE_LIST,
        ),
        AdPlaceholder(
            icon = RmIcons.Target,
            title = "AI가 지원 전략도 분석해 드려요",
            description = "채용공고를 목표에 추가하면 맞춤 전략을 만들어요.",
            ctaText = "목표 추가하러 가기 →",
            destination = AdDestination.TARGET_CREATE,
        ),
    )

    override fun pick(jobId: String): AdPlaceholder? = placeholders.selectByJobId(jobId)
}

/**
 * 작업 id로 플레이스홀더 하나를 결정적으로 고른다. 같은 id면 항상 같은 결과를 돌려준다(폴링 중 광고 고정).
 * 빈 리스트에는 적용하지 않는다(공급자가 비어 있지 않은 리스트로만 호출).
 */
internal fun List<AdPlaceholder>.selectByJobId(jobId: String): AdPlaceholder =
    this[(jobId.hashCode().absoluteValue) % size]
