package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable

@Composable
internal fun PickLocalMediaButton(
  iconSize: Dp,
  padding: Dp,
  onPickLocalMediaButtonClicked: () -> Unit
) {
  KurobaComposeIcon(
    modifier = Modifier
      .size(iconSize)
      .padding(padding)
      .kurobaClickable(
        bounded = false,
        onClick = onPickLocalMediaButtonClicked
      ),
    drawableId = R.drawable.ic_baseline_attach_file_24
  )
}