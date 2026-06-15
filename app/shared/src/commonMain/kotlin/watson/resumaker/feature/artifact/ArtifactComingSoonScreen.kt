package watson.resumaker.feature.artifact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import watson.resumaker.ui.component.AppScaffold
import watson.resumaker.ui.component.ComingSoon
import watson.resumaker.ui.component.ContentWidth
import watson.resumaker.ui.component.PageHeader
import watson.resumaker.ui.theme.pagePadding

/**
 * 디자인 시스템 §8.7 산출물 "준비 중". 백엔드 미구현 → 가짜 동작 금지.
 * 기록→겨냥 흐름으로 유도한다. 중앙 정렬 좁은 컨테이너(WX-1).
 */
@Composable
fun ArtifactComingSoonScreen(
    onBack: () -> Unit,
    onRecordExperience: () -> Unit,
    onAddTarget: () -> Unit,
    hasExperiences: Boolean = true,
) {
    AppScaffold(
        contentWidth = ContentWidth.NARROW,
        header = { windowSize ->
            PageHeader(
                title = "이력서·포트폴리오",
                horizontalPadding = windowSize.pagePadding(),
                onBack = onBack,
            )
        },
    ) { contentModifier, _ ->
        Box(modifier = contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ComingSoon(
                onRecordExperience = onRecordExperience,
                onAddTarget = onAddTarget,
                hasExperiences = hasExperiences,
            )
        }
    }
}
