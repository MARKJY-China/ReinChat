package com.reinchat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.ReinColors

@Composable
fun GlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> ReinColors.Danger.copy(alpha = 0.55f)
            focused -> ReinColors.Mint.copy(alpha = 0.36f)
            else -> Color.White.copy(alpha = 0.11f)
        },
        animationSpec = tween(280, easing = LiquidEase),
        label = "inputBorder",
    )
    val shape = RoundedCornerShape(17.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) ReinColors.Danger.copy(alpha = 0.9f) else ReinColors.TextTertiary,
        )
        Spacer(Modifier.heightIn(min = 7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = if (focused) 0.075f else 0.052f), shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 13.dp, vertical = if (singleLine) 12.dp else 11.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            leading?.let {
                it()
                Spacer(Modifier.width(9.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = if (singleLine) 20.dp else 44.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = ReinColors.TextTertiary.copy(alpha = 0.72f),
                        ),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    enabled = enabled,
                    textStyle = TextStyle(
                        color = if (enabled) ReinColors.TextPrimary else ReinColors.TextTertiary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(ReinColors.Mint, ReinColors.Cyan)),
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                )
            }
            trailing?.let {
                Spacer(Modifier.width(8.dp))
                it()
            }
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                color = if (isError) ReinColors.Danger.copy(alpha = 0.8f) else ReinColors.TextTertiary.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.045f), shape)
            .border(
                1.dp,
                if (focused) ReinColors.Mint.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                shape,
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReinColors.TextTertiary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(
                    color = ReinColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = Brush.verticalGradient(listOf(ReinColors.Mint, ReinColors.Cyan)),
                singleLine = true,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing.invoke()
        }
    }
}
