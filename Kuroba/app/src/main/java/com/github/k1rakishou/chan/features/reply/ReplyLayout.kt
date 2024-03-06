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
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
fun ReplyLayout(
  replyLayoutViewModel: ReplyLayoutViewModel,
  onPresolveCaptchaButtonClicked: () -> Unit,
  onSearchRemoteMediaButtonClicked: (ChanDescriptor) -> Unit,
) {
  val chanTheme = LocalChanTheme.current

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

  val replyLayoutAnimationState by replyLayoutState.replyLayoutAnimationState

  ReplyLayoutBottomSheet(
    modifier = Modifier.fillMaxSize(),
    replyLayoutState = replyLayoutState,
    replyLayoutAnimationState = replyLayoutAnimationState,
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
          onAttachedMediaLongClicked = replyLayoutViewModel::onAttachedMediaLongClicked,
          onRemoveAttachedMediaClicked = replyLayoutViewModel::removeAttachedMedia,
          onAttachableSelectionChanged = replyLayoutViewModel::onAttachableSelectionChanged,
          onAttachableStatusIconButtonClicked = replyLayoutViewModel::onAttachableStatusIconButtonClicked,
          onFlagSelectorClicked = replyLayoutViewModel::onFlagSelectorClicked,
          onPickLocalMediaButtonClicked = replyLayoutViewModel::onPickLocalMediaButtonClicked,
          onPickLocalMediaButtonLongClicked = replyLayoutViewModel::onPickLocalMediaButtonLongClicked,
          onPickRemoteMediaButtonClicked = replyLayoutViewModel::onPickRemoteMediaButtonClicked,
          onSearchRemoteMediaButtonClicked = { onSearchRemoteMediaButtonClicked(chanDescriptor) },
          onPresolveCaptchaButtonClicked = onPresolveCaptchaButtonClicked,
          onReplyLayoutOptionsButtonClicked = replyLayoutViewModel::onReplyLayoutOptionsButtonClicked
        )
      }
    }
  )
}