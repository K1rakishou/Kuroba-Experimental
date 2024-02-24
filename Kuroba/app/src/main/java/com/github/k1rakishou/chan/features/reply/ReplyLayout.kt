package com.github.k1rakishou.chan.features.reply

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.ui.compose.LocalChanTheme
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeDivider
import com.github.k1rakishou.chan.ui.compose.rememberViewModel
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

@Composable
fun ReplyLayout(chanDescriptor: ChanDescriptor) {
    val replyLayoutViewModel = rememberViewModel<ReplyLayoutViewModel>()

    val replyLayoutState = replyLayoutViewModel.replyLayoutStates.get(chanDescriptor)
    if (replyLayoutState == null) {
        return
    }

    val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility
    if (replyLayoutVisibility == ReplyLayoutVisibility.Collapsed) {
        return
    }

    val chanTheme = LocalChanTheme.current

    ReplyLayoutBottomSheet(
        replyLayoutState = replyLayoutState,
        chanTheme = chanTheme,
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
                    replyLayoutState = replyLayoutState,
                    replyLayoutViewModel = replyLayoutViewModel,
                    draggableStateProvider = { draggableState },
                    onDragStarted = onDragStarted,
                    onDragStopped = onDragStopped,
                    onCancelReplySendClicked = { replyLayoutViewModel.cancelSendReply(replyLayoutState) },
                    onSendReplyClicked = { replyLayoutViewModel.sendReply(chanDescriptor, replyLayoutState) },
                    onAttachedMediaClicked = { attachedMedia -> replyLayoutViewModel.onAttachedMediaClicked(attachedMedia) },
                    onRemoveAttachedMediaClicked = { attachedMedia -> replyLayoutViewModel.removeAttachedMedia(attachedMedia) },
                    onFlagSelectorClicked = { chanDescriptor -> replyLayoutViewModel.onFlagSelectorClicked(chanDescriptor) }
                )
            }
        }
    )
}