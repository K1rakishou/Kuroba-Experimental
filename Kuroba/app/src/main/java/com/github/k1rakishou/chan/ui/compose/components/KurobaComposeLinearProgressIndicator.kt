package com.github.k1rakishou.chan.ui.compose.components

import androidx.annotation.FloatRange
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeLinearProgressIndicator(
  modifier: Modifier = Modifier,
  overrideColor: Color? = null
) {
  val color = if (overrideColor == null) {
    val chanTheme = LocalChanTheme.current
    remember(key1 = chanTheme.accentColor) { Color(chanTheme.accentColor) }
  } else {
    overrideColor
  }

  LinearProgressIndicator(
    modifier = modifier,
    color = color
  )
}

@Composable
fun KurobaComposeLinearProgressIndicator(
  modifier: Modifier = Modifier,
  @FloatRange(from = 0.0, to = 1.0) progress: Float,
  overrideColor: Color? = null
) {
  val color = if (overrideColor == null) {
    val chanTheme = LocalChanTheme.current
    chanTheme.accentColorCompose
  } else {
    overrideColor
  }

  LinearProgressIndicator(
    modifier = modifier,
    progress = progress,
    color = color
  )
}