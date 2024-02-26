package com.github.k1rakishou.chan.features.reply

import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.reply.data.ReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply.left.ReplyInputLeftPart
import com.github.k1rakishou.chan.features.reply.right.ReplyInputRightPart
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.verticalScrollbar
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
fun ReplyLayoutContainer(
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: () -> Unit,
  onAttachedMediaClicked: (ReplyAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyAttachable) -> Unit,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val replyInputRightPartWidth = 58.dp
  val chanTheme = LocalChanTheme.current

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility

  val scrollState = rememberScrollState()
  val emptyPaddings = remember { PaddingValues() }

  val scrollModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier
      .verticalScroll(state = scrollState)
      .verticalScrollbar(
        contentPadding = emptyPaddings,
        scrollState = scrollState,
        thumbColor = chanTheme.accentColorCompose
      )
  } else {
    Modifier
  }

  val heightModifier = if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
    Modifier.wrapContentHeight()
  } else {
    Modifier.fillMaxHeight()
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(heightModifier),
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .then(scrollModifier)
    ) {
      ReplyInputLeftPart(
        replyLayoutState = replyLayoutState,
        replyLayoutViewModel = replyLayoutViewModel,
        onAttachedMediaClicked = onAttachedMediaClicked,
        onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
        onFlagSelectorClicked = onFlagSelectorClicked
      )
    }

    Box(
      modifier = Modifier
        .width(replyInputRightPartWidth)
        .fillMaxHeight()
    ) {
      ReplyInputRightPart(
        iconSize = 36.dp,
        replyLayoutState = replyLayoutState,
        draggableStateProvider = draggableStateProvider,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        onCancelReplySendClicked = onCancelReplySendClicked,
        onSendReplyClicked = onSendReplyClicked,
        onPickLocalMediaButtonClicked = replyLayoutViewModel::onPickLocalMediaButtonClicked,
        onPickRemoteMediaButtonClicked = replyLayoutViewModel::onPickRemoteMediaButtonClicked,
        onSearchRemoteMediaButtonClicked = replyLayoutViewModel::onSearchRemoteMediaButtonClicked,
        onPrefillCaptchaButtonClicked = replyLayoutViewModel::onPrefillCaptchaButtonClicked,
      )
    }
  }
}