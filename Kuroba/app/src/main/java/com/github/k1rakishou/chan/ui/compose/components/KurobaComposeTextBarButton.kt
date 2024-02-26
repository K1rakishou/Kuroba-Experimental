package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeTextBarButton(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  customTextColor: Color? = null,
  fontSize: TextUnit = 14.sp,
  onClick: () -> Unit,
  text: String,
) {
  val chanTheme = LocalChanTheme.current

  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      val textColor = customTextColor
        ?: chanTheme.textColorPrimaryCompose

      val modifiedTextColor = if (enabled) {
        textColor
      } else {
        textColor.copy(alpha = ContentAlpha.disabled)
      }

      KurobaComposeText(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.CenterVertically),
        text = text,
        color = modifiedTextColor,
        fontSize = fontSize,
        textAlign = TextAlign.Center
      )
    },
    elevation = null,
    colors = chanTheme.barButtonColors()
  )
}

@Composable
fun KurobaComposeTextBarButton(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  customTextColor: Color? = null,
  fontSize: TextUnit = 14.sp,
  onClick: () -> Unit,
  text: AnnotatedString,
) {
  val chanTheme = LocalChanTheme.current

  KurobaComposeButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    buttonContent = {
      val textColor = customTextColor
        ?: chanTheme.textColorPrimaryCompose

      val modifiedTextColor = if (enabled) {
        textColor
      } else {
        textColor.copy(alpha = ContentAlpha.disabled)
      }

      KurobaComposeText(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.CenterVertically),
        text = text,
        color = modifiedTextColor,
        fontSize = fontSize,
        textAlign = TextAlign.Center
      )
    },
    elevation = null,
    colors = chanTheme.barButtonColors()
  )
}