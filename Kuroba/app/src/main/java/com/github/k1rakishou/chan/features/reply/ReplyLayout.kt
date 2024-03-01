package com.github.k1rakishou.chan.features.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun ReplyLayout(
  replyLayoutViewModel: ReplyLayoutViewModel,
  onPresolveCaptchaButtonClicked: () -> Unit
) {
  val chanDescriptorMut by replyLayoutViewModel.boundChanDescriptor
  val chanDescriptor = chanDescriptorMut

  if (chanDescriptor == null) {
    return
  }

  val replyLayoutStateMut by replyLayoutViewModel.replyLayoutState
  val replyLayoutState = replyLayoutStateMut

  if (replyLayoutState == null) {
    return
  }

  val chanTheme = LocalChanTheme.current

  ReplyLayoutBottomSheet(
    modifier = Modifier.fillMaxSize(),
    replyLayoutState = replyLayoutState,
    chanTheme = chanTheme,
    onHeightSettled = { height -> replyLayoutState.onHeightChanged(height) },
    content = { targetHeight, draggableState, onDragStarted, onDragStopped ->
      if (targetHeight > 0.dp) {
        KurobaComposeDivider(modifier = Modifier.fillMaxWidth())
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(targetHeight)
      ) {
        ReplyLayoutContainer(
          chanDescriptor = chanDescriptor,
          replyLayoutState = replyLayoutState,
          replyLayoutViewModel = replyLayoutViewModel,
          draggableStateProvider = { draggableState },
          onDragStarted = onDragStarted,
          onDragStopped = onDragStopped,
          onCancelReplySendClicked = replyLayoutViewModel::cancelSendReply,
          onSendReplyClicked = replyLayoutViewModel::enqueueReply,
          onAttachedMediaClicked = replyLayoutViewModel::onAttachedMediaClicked,
          onRemoveAttachedMediaClicked = replyLayoutViewModel::removeAttachedMedia,
          onFlagSelectorClicked = replyLayoutViewModel::onFlagSelectorClicked,
          onPickLocalMediaButtonClicked = replyLayoutViewModel::onPickLocalMediaButtonClicked,
          onPickRemoteMediaButtonClicked = replyLayoutViewModel::onPickRemoteMediaButtonClicked,
          onSearchRemoteMediaButtonClicked = replyLayoutViewModel::onSearchRemoteMediaButtonClicked,
          onPresolveCaptchaButtonClicked = onPresolveCaptchaButtonClicked,
          onReplyLayoutOptionsButtonClicked = replyLayoutViewModel::onReplyLayoutOptionsButtonClicked
        )
      }
    }
  )
}