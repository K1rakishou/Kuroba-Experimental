package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme

internal val DefaultFillMaxSizeModifier: Modifier = Modifier.fillMaxSize()

internal fun Modifier.drawIndicatorLine(
  color: Color,
  lineWidth: Dp = 1.dp,
  verticalOffset: Dp = Dp.Unspecified
): Modifier {
  return drawBehind {
    val strokeWidth = lineWidth.value * density
    val y = size.height - strokeWidth / 2

    val drawFunc = {
      drawLine(
        color,
        Offset(0f, y),
        Offset(size.width, y),
        strokeWidth
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

private val defaultNoopClickCallback = { }

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.kurobaClickable(
  enabled: Boolean = true,
  bounded: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null
): Modifier {
  if (onLongClick == null && onClick == null) {
    error("At least one of the callbacks must be non-null")
  }

  return composed {
    val chanTheme = LocalChanTheme.current

    val color = remember(key1 = chanTheme) {
      if (chanTheme.isLightTheme) {
        Color(0x40000000)
      } else {
        Color(0x40ffffff)
      }
    }

    return@composed then(
      Modifier.combinedClickable(
        enabled = enabled,
        indication = rememberRipple(bounded = bounded, color = color),
        interactionSource = remember { MutableInteractionSource() },
        onLongClick = onLongClick,
        onClick = onClick ?: defaultNoopClickCallback
      )
    )
  }
}