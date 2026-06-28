package watson.resumaker.feature.artifact.ad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import watson.resumaker.ui.component.Badge
import watson.resumaker.ui.component.TextLink
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 대기 시간 광고 슬롯. 산출물 생성이 진행 중일 때 진행 카드와 완성 카드 사이에 1개 노출돼, 사용자가
 * 기다리는 동안 다음 행동([AdPlaceholder.destination])으로 안내한다. 외부 광고가 아닌 자기 홍보
 * 플레이스홀더지만, "광고" 배지와 정당화 카피로 광고 영역임을 명시한다(광고 명시 라벨 100%).
 *
 * 시각: RmCard(그림자 있음)를 재사용하지 않고 그림자 없는 옅은 컨테이너로 진행/완성 카드와 구분한다.
 * 접근성: 자식을 하나로 합쳐 "광고 영역"으로 읽히게 하고, 읽기 순서를 맨 뒤로 보낸다(콘텐츠 우선).
 * 임프레션: 합성에 등장한 1회만 [onImpression]을 호출한다.
 */
@Composable
fun AdSlot(
    placeholder: AdPlaceholder,
    modifier: Modifier = Modifier,
    onImpression: () -> Unit = {},
    onAdClick: () -> Unit = {},
    onNavigate: (AdDestination) -> Unit = {},
) {
    val colors = RmTheme.colors

    LaunchedEffect(placeholder) { onImpression() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RmRadius.card))
            .border(RmSize.hairline, colors.border, RoundedCornerShape(RmRadius.card))
            .padding(RmSpacing.space4)
            .semantics(mergeDescendants = true) {
                contentDescription = "광고 영역. ${placeholder.contentDescription}"
                traversalIndex = Float.MAX_VALUE
            },
    ) {
        // 광고 명시 라벨(은은한 톤): 슬롯이 광고 영역임을 항상 표기한다.
        Badge(text = "광고", fg = colors.textLabel, bg = colors.borderSubtle)

        Spacer(Modifier.height(RmSpacing.space3))

        // 본문: 좌측 아이콘 + 제목/설명/CTA. 최소 높이로 폴링 중 광고가 바뀌어도 레이아웃이 덜 출렁이게 한다.
        Box(Modifier.heightIn(min = 88.dp)) {
            Row {
                Icon(
                    imageVector = placeholder.icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .padding(end = RmSpacing.space3)
                        .size(24.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(RmSpacing.space1),
                ) {
                    Text(
                        text = placeholder.title,
                        style = RmTextStyles.bodyS.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = placeholder.description,
                        style = RmTextStyles.caption,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextLink(
                        text = placeholder.ctaText,
                        onClick = {
                            onAdClick()
                            onNavigate(placeholder.destination)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(RmSpacing.space3))

        // 정당화 카피: 왜 광고가 보이는지·언제 사라지는지 분명히 한다(투명성).
        Text(
            text = "AI 생성 비용은 광고로 충당돼요. 생성이 끝나면 바로 사라져요.",
            style = RmTextStyles.caption,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
