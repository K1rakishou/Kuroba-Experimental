package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R

@Composable
fun KurobaComposeSelectionIndicator(
  size: Dp = 24.dp,
  padding: Dp = 0.dp,
  enabled: Boolean = true,
  currentlySelected: Boolean,
  onSelectionChanged: (Boolean) -> Unit
) {
  val checkmark = painterResource(id = R.drawable.ic_blue_checkmark_24dp)

  val circleWidth = with(LocalDensity.current) { 2.dp.toPx() }
  val circleSize = with(LocalDensity.current) {
    remember(key1 = size) { Size(size.toPx() - (padding.toPx() * 2), size.toPx() - (padding.toPx() * 2)) }
  }
  val imageSize = remember(key1 = circleSize) {
    Size(circleSize.width + circleWidth, circleSize.height + circleWidth)
  }
  val style = remember(key1 = circleWidth) {
    Stroke(width = circleWidth)
  }

  var selected by remember(key1 = currentlySelected) { mutableStateOf(currentlySelected) }
  val alpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled

  Canvas(
    modifier = Modifier
      .size(size)
      .padding(padding)
      .kurobaClickable(
        enabled = enabled,
        bounded = false,
        onClick = {
          selected = !selected
          onSelectionChanged(selected)
        }
      ),
    onDraw = {
      drawArc(
        color = Color.White,
        size = circleSize,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        alpha = alpha,
        style = style
      )

      if (selected) {
        translate(left = -(circleWidth / 2), top = -(circleWidth / 2)) {
          with(checkmark) {
            draw(
              size = imageSize,
              alpha = alpha,
              colorFilter = null
            )
          }
        }
      }
    }
  )
}

