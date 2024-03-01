package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
internal fun SendReplyButton(
  iconSize: Dp,
  padding: Dp,
  chanDescriptor: ChanDescriptor,
  replyLayoutState: ReplyLayoutState,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: (ChanDescriptor) -> Unit
) {
  val replySendProgressMut by replyLayoutState.replySendProgressInPercentsState
  val replySendProgress = replySendProgressMut
  val sendReplyState by replyLayoutState.sendReplyState

  val buttonDrawableId = if (sendReplyState.canCancel) {
    R.drawable.ic_baseline_clear_24
  } else {
    R.drawable.ic_baseline_send_24
  }

  Box {
    KurobaComposeIcon(
      modifier = Modifier
        .size(iconSize)
        .padding(padding)
        .kurobaClickable(
          bounded = false,
          onClick = {
            if (sendReplyState.canCancel) {
              onCancelReplySendClicked()
            } else {
              onSendReplyClicked(chanDescriptor)
            }
          }
        ),
      drawableId = buttonDrawableId
    )

    if (replySendProgress >= 0) {
      Box(
        modifier = Modifier
          .wrapContentSize()
          .align(Alignment.TopEnd)
          .drawBehind {
            drawRoundRect(
              color = Color.Black.copy(alpha = 0.8f),
              cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx())
            )
          }
          .padding(horizontal = 4.dp)
      ) {
        KurobaComposeText(
          text = "${replySendProgress}%",
          color = Color.White,
          fontSize = 10.sp
        )
      }
    }
  }
}