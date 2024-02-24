package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.animatedHorizontalLine
import com.github.k1rakishou.chan.ui.compose.collectTextFontSize

@Composable
fun KurobaComposeTextField(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    fontSize: KurobaTextUnit = KurobaTextUnit(16.sp),
    maxLines: Int = Int.MAX_VALUE,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    textStyle: TextStyle = LocalTextStyle.current,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    shape: Shape = TextFieldDefaults.TextFieldShape,
    label: @Composable ((InteractionSource) -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val prevTextFieldValue by remember { mutableStateOf<TextFieldValue>(TextFieldValue(value)) }

    KurobaComposeTextField(
        value = prevTextFieldValue,
        modifier = modifier,
        onValueChange = { tfv: TextFieldValue -> onValueChange(tfv.text) },
        fontSize = fontSize,
        maxLines = maxLines,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        shape = shape,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interactionSource
    )
}

@Composable
fun KurobaComposeTextField(
    value: TextFieldValue,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit,
    fontSize: KurobaTextUnit = KurobaTextUnit(16.sp),
    maxLines: Int = Int.MAX_VALUE,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    textStyle: TextStyle = LocalTextStyle.current,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    shape: Shape = TextFieldDefaults.TextFieldShape,
    label: @Composable ((InteractionSource) -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val chanTheme = LocalChanTheme.current
    val view = LocalView.current

    DisposableEffect(
        key1 = view,
        effect = {
            if (view.isAttachedToWindow) {
                view.requestApplyInsets()
            }

            onDispose {
                if (view.isAttachedToWindow) {
                    view.requestApplyInsets()
                }
            }
        }
    )

    val textSelectionColors = remember(key1 = chanTheme.accentColor) {
        TextSelectionColors(
            handleColor = Color.Transparent,
            backgroundColor = chanTheme.accentColorCompose.copy(alpha = 0.4f)
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
        val textFontSize = collectTextFontSize(defaultFontSize = fontSize)
        val colors = chanTheme.textFieldColors()

        // If color is not provided via the text style, use content color as a default
        val textColor = textStyle.color.takeOrElse { colors.textColor(enabled).value }
        val mergedTextStyle = textStyle.merge(TextStyle(color = textColor, fontSize = textFontSize))

        val isFocused by interactionSource.collectIsFocusedAsState()

        @OptIn(ExperimentalMaterialApi::class)
        BasicTextField(
            value = value,
            modifier = modifier
                .background(colors.backgroundColor(enabled).value, shape)
                .animatedHorizontalLine(
                    enabled = enabled,
                    isError = isError,
                    isFocused = isFocused,
                    lineWidth = 2.dp
                )
                .defaultMinSize(
                    minWidth = TextFieldDefaults.MinWidth,
                    minHeight = TextFieldDefaults.MinHeight
                ),
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(colors.cursorColor(isError).value),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            decorationBox = @Composable { innerTextField ->
                val labelFunc: (@Composable (() -> Unit))? = if (label == null) {
                    null
                } else {
                    { label(interactionSource) }
                }

                TextFieldDefaults.TextFieldDecorationBox(
                    value = value.text,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    label = labelFunc,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    singleLine = singleLine,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    contentPadding = remember { PaddingValues(4.dp) }
                )
            }
        )
    }
}