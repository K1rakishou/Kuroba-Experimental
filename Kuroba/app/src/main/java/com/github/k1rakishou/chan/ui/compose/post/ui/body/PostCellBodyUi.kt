package com.github.k1rakishou.chan.ui.compose.post.ui.body

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState

@Composable
internal fun PostCellBodyUi(
  modifier: Modifier,
  threadState: ThreadState,
  postCellState: PostCellState
) {
  val postCommentMut by postCellState.postComment
  val postComment = postCommentMut

  val postCommentFontSizeMut by threadState.postCommentFontSize.collectAsState()
  val postCommentFontSize = postCommentFontSizeMut

  if (postComment == null || postCommentFontSize == null) {
    Shimmer(
      modifier = modifier
        then Modifier.height(42.dp)
    )

    return
  }

  KurobaComposeText(
    modifier = modifier,
    text = postComment
  )
}