package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.common.errorMessageOrClassName

@Composable
fun KurobaComposeErrorMessage(
  modifier: Modifier,
  error: Throwable
) {
  val errorMessage = remember(key1 = error) { error.errorMessageOrClassName() }

  KurobaComposeMessage(
    message = errorMessage,
    modifier = modifier
  )
}

@Composable
fun KurobaComposeMessage(
  modifier: Modifier,
  message: String
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    KurobaComposeText(
      text = message,
      fontSize = 18.ktu
    )
  }
}