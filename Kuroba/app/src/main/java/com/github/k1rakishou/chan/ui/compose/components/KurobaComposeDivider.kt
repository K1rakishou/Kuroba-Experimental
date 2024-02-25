package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeDivider(
  modifier: Modifier = Modifier,
  thickness: Dp = 1.dp,
  startIndent: Dp = 0.dp
) {
  val chanTheme = LocalChanTheme.current

  val indentMod = if (startIndent.value != 0f) {
    Modifier.padding(start = startIndent)
  } else {
    Modifier
  }

  val targetThickness = if (thickness == Dp.Hairline) {
    (1f / LocalDensity.current.density).dp
  } else {
    thickness
  }

  val dividerColorWithAlpha = remember(key1 = chanTheme.dividerColorCompose) {
    chanTheme.dividerColorCompose.copy(alpha = 0.1f)
  }

  Box(
      modifier
          .then(indentMod)
          .height(targetThickness)
          .background(color = dividerColorWithAlpha)
  )
}