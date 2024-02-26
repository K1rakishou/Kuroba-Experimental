package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.SendReplyState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
internal fun SendReplyButton(
  chanDescriptor: ChanDescriptor,
  replyLayoutState: ReplyLayoutState,
  sendReplyState: SendReplyState,
  iconSize: Dp,
  padding: Dp,
  onCancelReplySendClicked: (ReplyLayoutState) -> Unit,
  onSendReplyClicked: (ChanDescriptor, ReplyLayoutState) -> Unit,
  replySendProgress: Float?
) {
  val buttonDrawableId = remember(key1 = sendReplyState) {
    if (sendReplyState.canCancel) {
      R.drawable.ic_baseline_clear_24
    } else {
      R.drawable.ic_baseline_send_24
    }
  }

  KurobaComposeIcon(
    modifier = Modifier
      .size(iconSize)
      .padding(padding)
      .kurobaClickable(
        bounded = false,
        onClick = {
          if (sendReplyState.canCancel) {
            onCancelReplySendClicked(replyLayoutState)
          } else {
            onSendReplyClicked(chanDescriptor, replyLayoutState)
          }
        }
      ),
    drawableId = buttonDrawableId
  )

//  Box(
//    modifier = Modifier.size(iconSize)
//  ) {
//
//
//    // TODO: Change to a different way
//    if (replySendProgress != null) {
//      if (replySendProgress > 0f && replySendProgress < 1f) {
//        KurobaComposeProgressIndicator(
//          modifier = Modifier
//              .fillMaxSize()
//              .padding(4.dp),
//          progress = replySendProgress
//        )
//      } else if (replySendProgress >= 1f) {
//        KurobaComposeProgressIndicator(
//          modifier = Modifier
//              .fillMaxSize()
//              .padding(4.dp),
//        )
//      }
//    }
//  }
}