package com.github.k1rakishou.chan.ui.compose.post.ui.body

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberSingleInstanceCoroutineTask
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState

@Composable
internal fun PostCellCommentUi(
  modifier: Modifier,
  threadState: ThreadState,
  postCellState: PostCellState
) {
  val spoilerClickTask = rememberSingleInstanceCoroutineTask()

  val postDisplayOptions by threadState.postDisplayOptionsState
  val isInTextSelectionMode by threadState.isInTextSelectionMode

  val postCommentMut by postCellState.postComment
  val postComment = postCommentMut

  val postCommentFontSizeMut by threadState.postCommentFontSize.collectAsState()
  val postCommentFontSize = postCommentFontSizeMut

  val postCellTextState = postCellState.postCellTextState
  val postSpoilersMut by postCellTextState.postSpoilers
  val postSpoilers = postSpoilersMut

  LaunchedEffect(key1 = postCellTextState) {
    postCellTextState.init()
  }

  if (postComment == null || postCommentFontSize == null || postSpoilers == null) {
    Box(modifier = Modifier.height(42.dp))
    return
  }

  PostCellCommentSelectionWrapper(
    // TODO: compose post cells. ComposeCustomTextSelection needs to be updated!!!
    textSelectionEnabled = postDisplayOptions.textSelectionEnabled && !isInTextSelectionMode,
    onCopySelectedText = { selectedText ->
      threadState.onCopySelectedText(selectedText)
    },
    onQuoteSelectedText = { withText, selectedText ->
      threadState.onQuoteSelectedText(postCellState, withText, selectedText)
    },
    onTextSelectionModeChanged = { inSelectionMode ->
      threadState.onTextSelectionModeChanged(postCellState, inSelectionMode)
    }
  ) { textModifier, onTextLayout ->
    val isTextClickable = postDisplayOptions.detectLinkableClicks
      && !isInTextSelectionMode
      && !postDisplayOptions.postSelectionMode

    PostCellText(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .then(textModifier),
      postComment = postComment,
      postCellTextState = postCellTextState,
      isTextClickable = isTextClickable,
      inlineContent = inlinedContentForPostCellComment(postCellState),
      onSpoilerClicked = { clickedSpoiler ->
        spoilerClickTask.launch {
          threadState.onSpoilerClicked(postCellState.postDescriptor, clickedSpoiler)
        }
      },
      onTextLayout = onTextLayout
    )
  }
}