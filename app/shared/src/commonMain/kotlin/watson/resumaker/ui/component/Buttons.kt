package watson.resumaker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme
import watson.resumaker.ui.theme.rememberPressScale

/**
 * 디자인 시스템 §5.1 PrimaryButton.
 * fillMaxWidth, 56dp, radius 16, 텍스트 Bold/onPrimary. pressed 시 0.98 스케일.
 * [loading] 시 텍스트 자리에 인디케이터, 클릭 차단.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    pressedScale: Float = watson.resumaker.ui.theme.RmMotion.pressScale,
) {
    val colors = RmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale)
    // WX-17: 웹 hover 시 배경 톤을 한 단계 진하게(pressed 색) 바꿔 마우스 피드백을 준다.
    val hovered by interaction.collectIsHoveredAsState()
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = RoundedCornerShape(RmRadius.card),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (hovered) colors.primaryPressed else colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary.copy(alpha = 0.4f),
            disabledContentColor = colors.onPrimary.copy(alpha = 0.7f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.controlHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.onPrimary,
                strokeWidth = RmSpacing.space0_5,
                modifier = Modifier.size(RmSize.spinnerSm),
            )
        } else {
            Text(text = text, style = RmTextStyles.label.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * 디자인 시스템 §5.1 SecondaryButton (Neutral, slate-100).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = RmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(RmRadius.card),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.borderSubtle,
            contentColor = colors.textLabel,
            disabledContainerColor = colors.borderSubtle.copy(alpha = 0.4f),
            disabledContentColor = colors.textLabel.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(RmSize.controlHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Text(text = text, style = RmTextStyles.label.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * 디자인 시스템 §5.1 GhostButton (Outline, slate-200 테두리).
 * [fillWidth] = true(기본): fillMaxWidth 적용. false: 내용 폭에 맞춤(dialog dismissButton 등 인라인 배치 시 사용).
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    val colors = RmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(RmRadius.card),
        border = BorderStroke(RmSize.hairline, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textLabel,
            disabledContentColor = colors.textLabel.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(RmSize.controlHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Text(text = text, style = RmTextStyles.label.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * 디자인 시스템 §5.1 TextLink ("로그인하기" 등). bodyS + Bold + primary.
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    Box(modifier = modifier.height(RmSize.iconChipSmall), contentAlignment = Alignment.Center) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.primary),
        ) {
            Text(text = text, style = RmTextStyles.bodyS.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * 디자인 시스템 §5.1 InlineAddButton(WX-6). 리스트/섹션 헤더 인라인 "추가" 버튼(아이콘 + 라벨).
 * 데스크톱 하단 floating CTA를 대체한다. 웹 hover 시 배경 톤·테두리 강조(WX-15/17).
 */
@Composable
fun InlineAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .height(RmSize.iconChipSmall)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                if (hovered) colors.primaryContainer else colors.surfaceSubtle,
                RoundedCornerShape(RmRadius.card),
            )
            .border(RmSize.hairline, colors.primaryBorder, RoundedCornerShape(RmRadius.card))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = RmSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RmSpacing.space1),
    ) {
        Icon(
            imageVector = RmIcons.Add,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(RmSize.iconSm),
        )
        Text(
            text = text,
            style = RmTextStyles.bodyS.copy(fontWeight = FontWeight.Bold),
            color = colors.primary,
        )
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    RmTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(text = "가입하고 시작하기", onClick = {})
            SecondaryButton(text = "목표 추가하기", onClick = {})
            GhostButton(text = "건너뛰기", onClick = {})
            InlineAddButton(text = "기록하기", onClick = {})
            TextLink(text = "이미 계정이 있으신가요? 로그인", onClick = {})
        }
    }
}
