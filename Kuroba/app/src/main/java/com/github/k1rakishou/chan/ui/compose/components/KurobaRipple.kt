package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Stable
@Composable
fun rememberKurobaRipple(
  bounded: Boolean = true,
  radius: Dp = Dp.Unspecified,
  color: Color = Color.Unspecified
): IndicationNodeFactory {
  return remember(bounded, radius, color) { ripple(bounded, radius, color) }
}