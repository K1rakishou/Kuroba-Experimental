package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.reply.ReplyAttachments
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.chan.features.reply.data.SendReplyState
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor


@Composable
internal fun ReplyInputLeftPart(
    replyLayoutState: ReplyLayoutState,
    replyLayoutViewModel: ReplyLayoutViewModel,
    onAttachedMediaClicked: (ReplyAttachable) -> Unit,
    onRemoveAttachedMediaClicked: (ReplyAttachable) -> Unit,
    onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
    val focusManager = LocalFocusManager.current

    val sendReplyState by replyLayoutState.sendReplyState
    val replyLayoutVisibilityState by replyLayoutState.replyLayoutVisibility
    val attachables = replyLayoutState.attachables

    val replyLayoutEnabled = when (sendReplyState) {
        SendReplyState.Started,
        is SendReplyState.ReplySent -> false
        is SendReplyState.Finished -> true
    }

    LaunchedEffect(
        key1 = replyLayoutState.chanDescriptor,
        block = {
            // TODO: New reply layout
            //  replyLayoutViewModel.loadLastUsedFlag(replyLayoutState.chanDescriptor)
        }
    )

    ReplyLayoutLeftPartCustomLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        additionalInputsContent = {
            if (replyLayoutVisibilityState == ReplyLayoutVisibility.Expanded) {
                Column {
                    if (replyLayoutState.isCatalogMode) {
                        SubjectTextField(
                            replyLayoutState = replyLayoutState,
                            replyLayoutEnabled = replyLayoutEnabled,
                            onMoveFocus = { focusManager.moveFocus(FocusDirection.Down) }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    NameTextField(
                        replyLayoutState = replyLayoutState,
                        replyLayoutEnabled = replyLayoutEnabled,
                        onMoveFocus = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OptionsTextField(
                        replyLayoutState = replyLayoutState,
                        replyLayoutEnabled = replyLayoutEnabled,
                        onMoveFocus = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    // TODO: New reply layout
//                    FlagSelector(
//                        replyLayoutState = replyLayoutState,
//                        replyLayoutEnabled = replyLayoutEnabled,
//                        onFlagSelectorClicked = onFlagSelectorClicked
//                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        replyInputContent = {
            Column {
                ReplyTextField(
                    replyLayoutState = replyLayoutState,
                    replyLayoutEnabled = replyLayoutEnabled
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        formattingButtonsContent = {
            Column {
                ReplyFormattingButtons(replyLayoutState = replyLayoutState)

                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        replyAttachmentsContent = {
            if (attachables.isNotEmpty()) {
                ReplyAttachments(
                    replyLayoutState = replyLayoutState,
                    replyLayoutViewModel = replyLayoutViewModel,
                    onAttachedMediaClicked = onAttachedMediaClicked,
                    onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
                )
            }
        }
    )
}