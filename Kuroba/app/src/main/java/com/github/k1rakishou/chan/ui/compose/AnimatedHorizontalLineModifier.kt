package com.github.k1rakishou.chan.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified


fun Modifier.animatedHorizontalLine(
  enabled: Boolean,
  isError: Boolean,
  isFocused: Boolean,
  lineWidth: Dp = 1.dp,
  verticalOffset: Dp = Dp.Unspecified
): Modifier {
  return composed {
    val chanTheme = LocalChanTheme.current
    val disabledAlpha = ContentAlpha.disabled
    val highAlpha = ContentAlpha.high
    val mediumAlpha = ContentAlpha.medium

    val disabledIndicatorColor = remember(key1 = chanTheme) {
      chanTheme.defaultColors.controlNormalColorCompose.copy(alpha = disabledAlpha)
    }

    val errorIndicatorColor = chanTheme.errorColorCompose

    val focusedIndicatorColor = remember(key1 = chanTheme) {
      chanTheme.accentColorCompose.copy(alpha = highAlpha)
    }

    val unfocusedIndicatorColor = remember(key1 = chanTheme) {
      chanTheme.defaultColors.controlNormalColorCompose.copy(alpha = mediumAlpha)
    }

    val unfocusedLineColor = when {
      !enabled -> disabledIndicatorColor
      isError -> errorIndicatorColor
      else -> unfocusedIndicatorColor
    }

    val focusedLineColor = when {
      !enabled -> disabledIndicatorColor
      isError -> errorIndicatorColor
      else -> focusedIndicatorColor
    }

    val focusAnimationProgress by animateFloatAsState(
      targetValue = if (isFocused) 1f else 0f,
      animationSpec = tween(durationMillis = 250)
    )

    return@composed drawBehind {
      val strokeWidth = lineWidth.value * density
      val y = size.height - strokeWidth / 2

      val drawFunc = {
        val centerX = size.width / 2

        drawLine(
          color = unfocusedLineColor,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = strokeWidth
        )

        // Line animation from the centerX point to leftmost point
        drawLine(
          color = focusedLineColor,
          start = Offset(centerX, y),
          end = Offset(centerX - (centerX * focusAnimationProgress), y),
          strokeWidth = strokeWidth
        )

        // Line animation from the centerX point to rightmost point
        drawLine(
          color = focusedLineColor,
          start = Offset(centerX, y),
          end = Offset(centerX + (centerX * focusAnimationProgress), y),
          strokeWidth = strokeWidth
        )
      }

      if (verticalOffset.isSpecified) {
        translate(top = verticalOffset.toPx()) {
          drawFunc()
        }
      } else {
        drawFunc()
      }
    }
  }
}