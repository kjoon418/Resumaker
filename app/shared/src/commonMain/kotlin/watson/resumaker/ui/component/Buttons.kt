package watson.resumaker.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
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
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = RoundedCornerShape(RmRadius.card),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
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

@Preview
@Composable
private fun ButtonsPreview() {
    RmTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(text = "회원가입 완료", onClick = {})
            SecondaryButton(text = "목표 추가하기", onClick = {})
            GhostButton(text = "건너뛰기", onClick = {})
            TextLink(text = "이미 userId가 있으신가요? 재진입", onClick = {})
        }
    }
}
