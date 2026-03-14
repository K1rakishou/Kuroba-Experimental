package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeDivider(
  modifier: Modifier = Modifier,
  thickness: Dp = 1.dp,
  color: Color? = null
) {
  val chanTheme = LocalChanTheme.current

  val targetThickness = if (thickness == Dp.Hairline) {
    (1f / LocalDensity.current.density).dp
  } else {
    thickness
  }

  val dividerColor = color ?: chanTheme.dividerColorCompose

  Divider(
    modifier = modifier,
    color = dividerColor,
    thickness = targetThickness
  )
}