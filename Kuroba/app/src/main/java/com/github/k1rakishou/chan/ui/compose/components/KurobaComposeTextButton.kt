package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun KurobaComposeTextButton(
  onClick: () -> Unit,
  text: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      ComposeText(
        text = text,
        modifier = Modifier.fillMaxSize(),
        fontSize = 16.sp,
        textAlign = TextAlign.Center
      )
    }
  )
}