package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun KurobaComposeProgressIndicator(modifier: Modifier = Modifier) {
  Box(modifier = Modifier
    .fillMaxSize()
    .then(modifier)) {
    val chanTheme = LocalChanTheme.current
    val accentColor = remember(key1 = chanTheme.accentColor) {
      Color(chanTheme.accentColor)
    }

    CircularProgressIndicator(
      color = accentColor,
      modifier = Modifier
        .align(Alignment.Center)
        .size(42.dp, 42.dp)
    )
  }
}

@Composable
fun KurobaComposeErrorMessage(error: Throwable) {
  KurobaComposeErrorMessage(error.toString())
}

@Composable
fun KurobaComposeErrorMessage(errorMessage: String) {
  Box(modifier = Modifier
    .fillMaxSize()
    .padding(8.dp)
  ) {
    KurobaComposeText(errorMessage, modifier = Modifier.align(Alignment.Center))
  }
}

@Composable
fun KurobaComposeText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
  KurobaComposeText(text = AnnotatedString(text), modifier = modifier, color = color)
}

@Composable
fun KurobaComposeText(text: AnnotatedString, modifier: Modifier = Modifier, color: Color? = null) {
  val textColorPrimary = if (color == null) {
    val chanTheme = LocalChanTheme.current

    remember(key1 = chanTheme.textColorPrimary) {
      Color(chanTheme.textColorPrimary)
    }
  } else {
    color
  }

  Text(
    color = textColorPrimary,
    text = text,
    modifier = modifier
  )
}

@Composable
fun KurobaComposeTextField(
  value: String,
  modifier: Modifier = Modifier,
  onValueChange: (String) -> Unit,
  maxLines: Int = Int.MAX_VALUE,
  label: @Composable (() -> Unit)? = null,
) {
  val chanTheme = LocalChanTheme.current

  TextField(
    value = value,
    label = label,
    onValueChange = onValueChange,
    maxLines = maxLines,
    modifier = modifier,
    colors = chanTheme.textFieldColors()
  )
}