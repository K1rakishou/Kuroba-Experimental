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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import com.github.k1rakishou.chan.ui.compose.KurobaTextUnit
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaLabelText(
  enabled: Boolean = true,
  labelText: String?,
  fontSize: KurobaTextUnit = 13.ktu,
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

@Composable
fun KurobaLabelText(
  enabled: Boolean = true,
  labelText: AnnotatedString?,
  color: Color? = null,
  fontSize: KurobaTextUnit = 13.ktu
) {
  if (labelText == null) {
    return
  }

  AnimatedVisibility(
    visible = true,
    enter = fadeIn(),
    exit = fadeOut()
  ) {
    val textAlpha = if (enabled) {
      ContentAlpha.high
    } else {
      ContentAlpha.disabled
    }

    KurobaComposeText(
      modifier = Modifier.graphicsLayer { alpha = textAlpha },
      text = labelText,
      fontSize = fontSize,
      color = color
    )
  }
}