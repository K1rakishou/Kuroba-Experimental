package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InsetsAwareBox(
  modifier: Modifier = Modifier,
  contentAlignment: Alignment = Alignment.TopStart,
  applyTop: Boolean = true,
  applyBottom: Boolean = true,
  insets: PaddingValues,
  additionalPaddings: PaddingValues = remember { PaddingValues() },
  content: @Composable () -> Unit
) {

  Box(
    modifier = modifier.then(
      Modifier
        .padding(
          top = insets.calculateTopPadding().takeIf { applyTop }
            ?.plus(additionalPaddings.calculateTopPadding())
            ?: 0.dp,
          bottom = insets.calculateBottomPadding().takeIf { applyBottom }
            ?.plus(additionalPaddings.calculateBottomPadding())
            ?: 0.dp
        )
    ),
    contentAlignment = contentAlignment
  ) {
    content()
  }
}