package watson.resumaker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import watson.resumaker.ui.theme.RmIcons
import watson.resumaker.ui.theme.RmRadius
import watson.resumaker.ui.theme.RmSize
import watson.resumaker.ui.theme.RmSpacing
import watson.resumaker.ui.theme.RmTextStyles
import watson.resumaker.ui.theme.RmTheme

/**
 * 디자인 시스템 §5.2 RmTextField.
 * 라벨(위) + 입력박스(56dp, slate-50, slate-200 테두리, focus 시 primary) + 헬퍼/에러(아래).
 * multiline 지원(본문/채용방향): [minHeight]로 높이 확장, top-align.
 *
 * @param error null이 아니면 에러 상태(테두리 danger + 메시지 표시).
 */
@Composable
fun RmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = RmSize.controlHeight,
    /** IME 액션 버튼(다음 필드 이동 Next / 마지막 필드 제출 Done). */
    imeAction: ImeAction = ImeAction.Default,
    /** imeAction 트리거 시 콜백(예: Next→다음 포커스, Done→제출). */
    onImeAction: (() -> Unit)? = null,
    /** 외부에서 이 필드에 포커스를 옮기기 위한 핸들(키보드 흐름의 "다음 필드"). */
    focusRequester: FocusRequester? = null,
) {
    val colors = RmTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val borderColor = when {
        error != null -> colors.danger
        focused -> colors.primary
        else -> colors.border
    }
    val boxBg = if (enabled) colors.surfaceSubtle else colors.surfaceDisabled

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = RmTextStyles.label.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textLabel,
            modifier = Modifier.padding(start = RmSpacing.space1, bottom = RmSpacing.space2),
        )

        val visualTransformation: VisualTransformation =
            if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            interactionSource = interaction,
            textStyle = RmTextStyles.bodyMr.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction?.invoke() },
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
                onSend = { onImeAction?.invoke() },
                onSearch = { onImeAction?.invoke() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                // #7 한글 IME 알려진 한계(미해결): Wasm/Canvas 환경에서 멀티라인 입력 시 한글 조합 중
                // 첫 Enter가 개행이 아닌 글자 완성(IME commit)으로 소비된다. onPreviewKeyEvent로 Enter를
                // 가로채 직접 개행을 넣는 우회는 (a) value가 plain String이라 커서 위치를 알 수 없어 항상
                // 문자열 끝에 개행이 붙고 커서가 끝으로 점프하며, (b) 정상 동작하던 입력기·비-IME 경로의
                // 기본 개행까지 깨뜨려, 멀티라인 8개 필드(경험 본문/채용방향/양식·산출물 편집 등) 중간
                // 편집 UX를 오히려 악화시킨다. 따라서 우회를 적용하지 않고 표준 BasicTextField 동작을
                // 유지한다. 근본 해결은 Compose 런타임의 Wasm IME 이벤트 처리 개선이 필요하다
                // (참고: KT-65566 / compose-multiplatform#4220).
                .background(boxBg, RoundedCornerShape(RmRadius.card))
                .border(RmSize.hairline, borderColor, RoundedCornerShape(RmRadius.card)),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    modifier = Modifier.padding(
                        horizontal = RmSpacing.space4,
                        vertical = if (singleLine) RmSpacing.space1 else RmSpacing.space4,
                    ),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = RmTextStyles.bodyMr,
                                color = colors.textTertiary,
                            )
                        }
                        inner()
                    }
                    if (isPassword) {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) RmIcons.EyeOff else RmIcons.Eye,
                                contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(RmSize.iconMd),
                            )
                        }
                    }
                }
            },
        )

        val message = error ?: helper
        if (message != null) {
            Text(
                text = message,
                style = RmTextStyles.caption,
                color = if (error != null) colors.danger else colors.textSecondary,
                modifier = Modifier.padding(start = RmSpacing.space1, top = RmSpacing.space1),
            )
        }
    }
}

@Preview
@Composable
private fun RmTextFieldPreview() {
    RmTheme {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            RmTextField(value = "", onValueChange = {}, label = "이메일", placeholder = "name@example.com")
            RmTextField(
                value = "abc",
                onValueChange = {},
                label = "비밀번호",
                isPassword = true,
                error = "비밀번호는 8자 이상으로 입력해 주세요.",
            )
        }
    }
}
