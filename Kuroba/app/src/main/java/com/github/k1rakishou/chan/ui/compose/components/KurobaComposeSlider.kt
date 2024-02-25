package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  steps: Int = 0
) {
  val chanTheme = LocalChanTheme.current

  Slider(
    value = value,
    modifier = modifier,
    steps = steps,
    onValueChange = onValueChange,
    colors = chanTheme.sliderColors()
  )
}