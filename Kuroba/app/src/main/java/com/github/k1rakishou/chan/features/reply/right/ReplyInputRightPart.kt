package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.SendReplyState
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeProgressIndicator
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable

@Composable
internal fun ReplyInputRightPart(
  iconSize: Dp,
  replyLayoutState: ReplyLayoutState,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val replySendProgressMut by replyLayoutState.replySendProgressState
  val replySendProgress = replySendProgressMut
  val sendReplyState by replyLayoutState.sendReplyState

  val padding = with(density) { 8.dp.toPx() }
  val cornerRadius = with(density) { remember { CornerRadius(8.dp.toPx(), 8.dp.toPx()) } }

  Column(
    modifier = Modifier
        .fillMaxSize()
        .draggable(
            state = draggableStateProvider(),
            orientation = Orientation.Vertical,
            onDragStarted = { onDragStarted() },
            onDragStopped = { velocity -> onDragStopped(velocity) }
        )
        .drawBehind {
            drawRoundRect(
                color = chanTheme.backColorSecondaryCompose,
                topLeft = Offset(x = padding, y = padding),
                size = Size(
                    width = this.size.width - (padding * 2),
                    height = this.size.height - (padding * 2)
                ),
                alpha = 0.4f,
                cornerRadius = cornerRadius
            )
        },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    SendReplyButton(
      sendReplyState = sendReplyState,
      iconSize = iconSize,
      onCancelReplySendClicked = onCancelReplySendClicked,
      onSendReplyClicked = onSendReplyClicked,
      replySendProgress = replySendProgress
    )
  }
}

@Composable
private fun SendReplyButton(
  sendReplyState: SendReplyState,
  iconSize: Dp,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  replySendProgress: Float?
) {
  val buttonDrawableId = remember(key1 = sendReplyState) {
    if (sendReplyState.canCancel) {
      R.drawable.ic_baseline_clear_24
    } else {
      R.drawable.ic_baseline_send_24
    }
  }

  val padding = if (sendReplyState.canCancel) {
    10.dp
  } else {
    4.dp
  }

  Box(
    modifier = Modifier.size(iconSize)
  ) {
    KurobaComposeIcon(
      modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .kurobaClickable(
              bounded = false,
              onClick = {
                  if (sendReplyState.canCancel) {
                      onCancelReplySendClicked()
                  } else {
                      onSendReplyClicked()
                  }
              }
          ),
      drawableId = buttonDrawableId
    )

    if (replySendProgress != null) {
      if (replySendProgress > 0f && replySendProgress < 1f) {
        KurobaComposeProgressIndicator(
          modifier = Modifier
              .fillMaxSize()
              .padding(4.dp),
          progress = replySendProgress
        )
      } else if (replySendProgress >= 1f) {
        KurobaComposeProgressIndicator(
          modifier = Modifier
              .fillMaxSize()
              .padding(4.dp),
        )
      }
    }
  }
}