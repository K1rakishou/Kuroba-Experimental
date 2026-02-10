package com.github.k1rakishou.chan.features.reply.right

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeIcon
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
internal fun ReplyInputRightPart(
  iconSize: Dp,
  chanDescriptor: ChanDescriptor,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: (ChanDescriptor) -> Unit,
  onPresolveCaptchaButtonClicked: () -> Unit,
  onReplyLayoutPickFileButtonClicked: () -> Unit,
  onReplyLayoutPickFileButtonLongClicked: () -> Unit,
  onReplyLayoutOptionsButtonClicked: () -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val padding = with(density) { 8.dp.toPx() }
  val cornerRadius = with(density) { remember { CornerRadius(8.dp.toPx(), 8.dp.toPx()) } }

  val displayCaptchaPresolveButton by replyLayoutState.displayCaptchaPresolveButton
  val newReplyLayoutTutorialFinished by replyLayoutViewModel.newReplyLayoutTutorialFinished.collectAsState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 4.dp)
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
          alpha = 0.6f,
          cornerRadius = cornerRadius
        )
      }
      .replyLayoutDragTutorial(
        enabled = !newReplyLayoutTutorialFinished,
        cornerRadius = cornerRadius
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.height(6.dp))

    SendReplyButton(
      iconSize = iconSize,
      padding = 4.dp,
      newReplyLayoutTutorialFinished = newReplyLayoutTutorialFinished,
      chanDescriptor = chanDescriptor,
      replyLayoutState = replyLayoutState,
      onCancelReplySendClicked = onCancelReplySendClicked,
      onSendReplyClicked = onSendReplyClicked,
    )

    AnimatedVisibility(visible = displayCaptchaPresolveButton) {
      Spacer(modifier = Modifier.height(6.dp))

      PresolveCaptchaButton(
        iconSize = iconSize,
        padding = 4.dp,
        newReplyLayoutTutorialFinished = newReplyLayoutTutorialFinished,
        replyLayoutViewModel = replyLayoutViewModel,
        onPresolveCaptchaButtonClicked = onPresolveCaptchaButtonClicked
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    KurobaComposeIcon(
      modifier = Modifier
        .size(iconSize)
        .padding(4.dp)
        .kurobaClickable(
          bounded = false,
          enabled = newReplyLayoutTutorialFinished,
          onClick = onReplyLayoutPickFileButtonClicked,
          onLongClick = onReplyLayoutPickFileButtonLongClicked
        ),
      drawableId = R.drawable.ic_baseline_attach_file_24
    )

    Spacer(modifier = Modifier.height(6.dp))

    ReplyLayoutOptionsButton(
      iconSize = iconSize,
      padding = 4.dp,
      newReplyLayoutTutorialFinished = newReplyLayoutTutorialFinished,
      onReplyLayoutOptionsButtonClicked = onReplyLayoutOptionsButtonClicked
    )
  }
}