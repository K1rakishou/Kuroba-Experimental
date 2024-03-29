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
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
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
  onPickLocalMediaButtonClicked: () -> Unit,
  onPickLocalMediaButtonLongClicked: () -> Unit,
  onPickRemoteMediaButtonClicked: () -> Unit,
  onSearchRemoteMediaButtonClicked: () -> Unit,
  onPresolveCaptchaButtonClicked: () -> Unit,
  onReplyLayoutOptionsButtonClicked: () -> Unit,
) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val padding = with(density) { 8.dp.toPx() }
  val cornerRadius = with(density) { remember { CornerRadius(8.dp.toPx(), 8.dp.toPx()) } }

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility
  val displayCaptchaPresolveButton by replyLayoutState.displayCaptchaPresolveButton
  val attachables by replyLayoutState.attachables
  val syntheticAttachables = replyLayoutState.syntheticAttachables

  var buttonCounter = 0
  val maxButtonsCount = when {
    replyLayoutVisibility == ReplyLayoutVisibility.Expanded -> Int.MAX_VALUE
    attachables.attachables.isEmpty() && syntheticAttachables.isEmpty() -> 4
    else -> 6
  }

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
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // TODO: implement a custom layout and add buttons based on the current height of ReplyInputRightPart?
    if (buttonCounter++ < maxButtonsCount) {
      Spacer(modifier = Modifier.height(6.dp))

      SendReplyButton(
        iconSize = iconSize,
        padding = 4.dp,
        chanDescriptor = chanDescriptor,
        replyLayoutState = replyLayoutState,
        onCancelReplySendClicked = onCancelReplySendClicked,
        onSendReplyClicked = onSendReplyClicked,
      )
    }

    if (buttonCounter++ < maxButtonsCount) {
      AnimatedVisibility(visible = displayCaptchaPresolveButton) {
        Spacer(modifier = Modifier.height(6.dp))

        PresolveCaptchaButton(
          iconSize = iconSize,
          padding = 4.dp,
          replyLayoutViewModel = replyLayoutViewModel,
          onPresolveCaptchaButtonClicked = onPresolveCaptchaButtonClicked
        )
      }
    }

    if (buttonCounter++ < maxButtonsCount) {
      Spacer(modifier = Modifier.height(6.dp))

      PickLocalMediaButton(
        iconSize = iconSize,
        padding = 4.dp,
        onPickLocalMediaButtonClicked = onPickLocalMediaButtonClicked,
        onPickLocalMediaButtonLongClicked = onPickLocalMediaButtonLongClicked
      )
    }

    if (buttonCounter++ < maxButtonsCount) {
      Spacer(modifier = Modifier.height(6.dp))

      SearchRemoteMediaButton(
        iconSize = iconSize,
        padding = 4.dp,
        onSearchRemoteMediaButtonClicked = onSearchRemoteMediaButtonClicked
      )
    }

    if (buttonCounter++ < maxButtonsCount) {
      Spacer(modifier = Modifier.height(6.dp))

      PickRemoteMediaButton(
        iconSize = iconSize,
        padding = 4.dp,
        onPickRemoteMediaButtonClicked = onPickRemoteMediaButtonClicked
      )
    }

    if (buttonCounter++ < maxButtonsCount) {
      Spacer(modifier = Modifier.height(6.dp))

      ReplyLayoutOptionsButton(
        iconSize = iconSize,
        padding = 4.dp,
        onReplyLayoutOptionsButtonClicked = onReplyLayoutOptionsButtonClicked
      )
    }
  }
}