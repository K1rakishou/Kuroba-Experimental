package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
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
fun KurobaComposeText(text: String, modifier: Modifier = Modifier) {
  KurobaComposeText(text = AnnotatedString(text), modifier)
}

@Composable
fun KurobaComposeText(text: AnnotatedString, modifier: Modifier = Modifier) {
  val chanTheme = LocalChanTheme.current
  val textColorPrimary = remember(key1 = chanTheme.textColorPrimary) {
    Color(chanTheme.textColorPrimary)
  }

  Text(
    color = textColorPrimary,
    text = text,
    modifier = modifier
  )
}