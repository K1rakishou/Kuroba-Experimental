package com.github.k1rakishou.chan.features.reply.left

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.features.reply.ReplyLayoutViewModel
import com.github.k1rakishou.chan.features.reply.data.ReplyFileAttachable
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutState
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor


@Composable
internal fun ReplyInputLeftPart(
  replyLayoutEnabled: Boolean,
  replyLayoutState: ReplyLayoutState,
  replyLayoutViewModel: ReplyLayoutViewModel,
  onAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachedMediaLongClicked: (ReplyFileAttachable) -> Unit,
  onRemoveAttachedMediaClicked: (ReplyFileAttachable) -> Unit,
  onAttachableSelectionChanged: (ReplyFileAttachable, Boolean) -> Unit,
  onAttachableStatusIconButtonClicked: (ReplyFileAttachable) -> Unit,
  onFlagSelectorClicked: (ChanDescriptor) -> Unit
) {
  val focusManager = LocalFocusManager.current

  val replyLayoutVisibility by replyLayoutState.replyLayoutVisibility

  Column(
    modifier = Modifier
      .padding(horizontal = 8.dp)
  ) {
    if (replyLayoutVisibility == ReplyLayoutVisibility.Expanded) {
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

      Spacer(modifier = Modifier.height(4.dp))

      FlagSelector(
        replyLayoutEnabled = replyLayoutEnabled,
        replyLayoutState = replyLayoutState,
        replyLayoutViewModel = replyLayoutViewModel,
        onFlagSelectorClicked = onFlagSelectorClicked
      )

      Spacer(modifier = Modifier.height(4.dp))
    }

    ReplyTextField(
      replyLayoutState = replyLayoutState,
      replyLayoutViewModel = replyLayoutViewModel,
      replyLayoutEnabled = replyLayoutEnabled
    )

    Spacer(modifier = Modifier.height(4.dp))

    ReplyFormattingButtons(
      replyLayoutEnabled = replyLayoutEnabled,
      replyLayoutState = replyLayoutState
    )

    Spacer(modifier = Modifier.height(4.dp))

    ReplyAttachments(
      replyLayoutEnabled = replyLayoutEnabled,
      replyLayoutState = replyLayoutState,
      replyLayoutViewModel = replyLayoutViewModel,
      onAttachedMediaClicked = onAttachedMediaClicked,
      onAttachedMediaLongClicked = onAttachedMediaLongClicked,
      onRemoveAttachedMediaClicked = onRemoveAttachedMediaClicked,
      onAttachableSelectionChanged = onAttachableSelectionChanged,
      onAttachableStatusIconButtonClicked = onAttachableStatusIconButtonClicked
    )
  }
}