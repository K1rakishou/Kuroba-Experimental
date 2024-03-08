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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply.left.ReplyInputLeftPart
import com.github.k1rakishou.chan.features.reply.right.ReplyInputRightPart
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.verticalScrollbar
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
fun ReplyLayoutContainer(
  chanDescriptor: ChanDescriptor,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  draggableStateProvider: () -> DraggableState,
  onDragStarted: suspend () -> Unit,
  onDragStopped: suspend (velocity: Float) -> Unit,
  onCancelReplySendClicked: () -> Unit,
  onSendReplyClicked: (ChanDescriptor) -> Unit,
  onAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachedMediaLongClicked: (ReplyFileAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachableSelectionChanged: (ReplyFileAttachable, Boolean) -> Unit,
  onAttachableStatusIconButtonClicked: (ReplyFileAttachable) -> Unit,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit,
  onPickLocalMediaButtonClicked: () -> Unit,
  onPickLocalMediaButtonLongClicked: () -> Unit,
  onPickRemoteMediaButtonClicked: () -> Unit,
  onSearchRemoteMediaButtonClicked: () -> Unit,
  onPresolveCaptchaButtonClicked: () -> Unit,
  onReplyLayoutOptionsButtonClicked: () -> Unit,
) {
  val replyInputRightPartWidth = 58.dp
  val iconSize = 36.dp

  val chanTheme = LocalChanTheme.current

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility
  val sendReplyState by replyLayoutState.sendReplyState
  val replyLayoutEnabled = sendReplyState.replyLayoutEnabled

  val scrollState = rememberScrollState()
  val emptyPaddings = remember { PaddingValues() }

  LaunchedEffect(key1 = replyLayoutVisibility) {
    scrollState.scrollTo(0)
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
        .verticalScroll(
          enabled = replyLayoutVisibility == ReplyLayoutVisibility.Expanded,
          state = scrollState
        )
        .verticalScrollbar(
          contentPadding = emptyPaddings,
          scrollState = scrollState,
          thumbColor = chanTheme.accentColorCompose
        )
    ) {
      ReplyInputLeftPart(
        replyLayoutEnabled = replyLayoutEnabled,
        replyLayoutState = replyLayoutState,
        replyLayoutViewModel = replyLayoutViewModel,
        onAttachedMediaClicked = onAttachedMediaClicked,
        onAttachedMediaLongClicked = onAttachedMediaLongClicked,
        onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
        onAttachableSelectionChanged = onAttachableSelectionChanged,
        onAttachableStatusIconButtonClicked = onAttachableStatusIconButtonClicked,
        onFlagSelectorClicked = onFlagSelectorClicked
      )
    }

    Box(
      modifier = Modifier
        .width(replyInputRightPartWidth)
        .fillMaxHeight()
    ) {
      ReplyInputRightPart(
        iconSize = iconSize,
        chanDescriptor = chanDescriptor,
        replyLayoutState = replyLayoutState,
        replyLayoutViewModel = replyLayoutViewModel,
        draggableStateProvider = draggableStateProvider,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        onCancelReplySendClicked = onCancelReplySendClicked,
        onSendReplyClicked = onSendReplyClicked,
        onPickLocalMediaButtonClicked = onPickLocalMediaButtonClicked,
        onPickLocalMediaButtonLongClicked = onPickLocalMediaButtonLongClicked,
        onPickRemoteMediaButtonClicked = onPickRemoteMediaButtonClicked,
        onSearchRemoteMediaButtonClicked = onSearchRemoteMediaButtonClicked,
        onPresolveCaptchaButtonClicked = onPresolveCaptchaButtonClicked,
        onReplyLayoutOptionsButtonClicked = onReplyLayoutOptionsButtonClicked
      )
    }
  }
}