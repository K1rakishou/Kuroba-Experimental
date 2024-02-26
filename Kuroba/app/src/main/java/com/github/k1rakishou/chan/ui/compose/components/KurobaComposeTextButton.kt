package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeTextButton(
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  customTextColor: Color? = null
) {
  val chanTheme = LocalChanTheme.current

  val textColor = customTextColor
    ?: chanTheme.backColorCompose

  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      ComposeText(
        text = text,
        modifier = Modifier.fillMaxSize(),
        fontSize = 16.sp,
        color = textColor,
        textAlign = TextAlign.Center
      )
    }
  )
}