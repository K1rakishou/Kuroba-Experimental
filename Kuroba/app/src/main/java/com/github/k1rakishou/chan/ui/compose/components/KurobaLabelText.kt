package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaLabelText(
    enabled: Boolean = true,
    labelText: String?,
    fontSize: TextUnit = 13.sp,
    interactionSource: InteractionSource
) {
    if (labelText == null) {
        return
    }

    val chanTheme = LocalChanTheme.current
    val isFocused by interactionSource.collectIsFocusedAsState()

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val alpha = if (enabled) {
            ContentAlpha.high
        } else {
            ContentAlpha.disabled
        }

        val hintColor = remember(alpha, isFocused) {
            if (isFocused && enabled) {
                return@remember chanTheme.accentColorCompose.copy(alpha = alpha)
            }

            return@remember chanTheme.textColorHintCompose.copy(alpha = alpha)
        }

        val hintColorAnimated by animateColorAsState(
            targetValue = hintColor,
            label = "Hint text color animated"
        )

        KurobaComposeText(
            text = labelText,
            fontSize = fontSize,
            color = hintColorAnimated
        )
    }
}