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
internal fun ReplyLayoutOptionsButton(
  iconSize: Dp,
  padding: Dp,
  onReplyLayoutOptionsButtonClicked: () -> Unit
) {
  KurobaComposeIcon(
    modifier = Modifier
      .size(iconSize)
      .padding(padding)
      .kurobaClickable(
        bounded = false,
        onClick = onReplyLayoutOptionsButtonClicked
      ),
    drawableId = R.drawable.ic_more_vert_white_24dp
  )
}