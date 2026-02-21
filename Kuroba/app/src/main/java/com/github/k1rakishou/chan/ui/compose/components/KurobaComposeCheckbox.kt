package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeCheckbox(
  modifier: Modifier = Modifier,
  currentlyChecked: Boolean,
  onCheckChanged: (Boolean) -> Unit,
  text: String? = null,
  enabled: Boolean = true
) {
  val chanTheme = LocalChanTheme.current
  var isChecked by remember(key1 = currentlyChecked) { mutableStateOf(currentlyChecked) }

  val color = remember(key1 = chanTheme) {
    if (chanTheme.isLightTheme) {
      Color(0x40000000)
    } else {
      Color(0x40ffffff)
    }
  }

  val clickable = if (enabled) {
    Modifier
      .clickable(
        enabled = true,
        interactionSource = remember { MutableInteractionSource() },
        indication = rememberKurobaRipple(bounded = true, color = color),
        onClick = {
          isChecked = isChecked.not()
          onCheckChanged(isChecked)
        }
      )
  } else {
    Modifier
  }

  Row(
    modifier = Modifier
      .then(clickable)
      .then(modifier)
  ) {
    Checkbox(
      modifier = Modifier
        .align(Alignment.CenterVertically),
      checked = isChecked,
      enabled = enabled,
      onCheckedChange = { checked ->
        isChecked = checked
        onCheckChanged(isChecked)
      },
      colors = chanTheme.checkBoxColors()
    )

    if (text != null) {
      Spacer(modifier = Modifier.width(8.dp))

      KurobaComposeText(
        modifier = Modifier.align(Alignment.CenterVertically),
        text = text,
        enabled = enabled
      )
    }
  }
}