package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.material.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeSwitch(
  modifier: Modifier = Modifier,
  initiallyChecked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Switch(
    checked = initiallyChecked,
    onCheckedChange = onCheckedChange,
    colors = chanTheme.switchColors(),
    modifier = modifier
  )
}