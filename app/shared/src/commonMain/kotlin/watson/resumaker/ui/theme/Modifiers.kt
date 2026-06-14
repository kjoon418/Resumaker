package watson.resumaker.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 디자인 시스템 §4.5 눌림 스케일. interactionSource의 pressed 상태에 따라
 * scale을 [RmMotion.durFast] 동안 보간한다(버튼/카드 공통).
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = RmMotion.pressScale,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(RmMotion.durFast),
        label = "pressScale",
    )
    return scale
}

/**
 * 자체 탭 제스처로 눌림 스케일을 적용하는 Modifier(클릭 가능 카드 등 interactionSource를 직접 안 쓰는 경우).
 */
fun Modifier.pressScale(
    pressedScale: Float = RmMotion.pressScale,
    onClick: () -> Unit,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(RmMotion.durFast),
        label = "pressScaleTap",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    val released = tryAwaitRelease()
                    pressed = false
                    if (released) onClick()
                },
            )
        }
}
