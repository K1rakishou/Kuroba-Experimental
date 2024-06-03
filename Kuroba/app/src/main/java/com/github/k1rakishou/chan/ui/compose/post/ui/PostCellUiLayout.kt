package com.github.k1rakishou.chan.ui.compose.post.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState

@Composable
internal fun PostCellUiLayout(
  modifier: Modifier,
  postCellState: PostCellState,
  title: @Composable () -> Unit,
  body: @Composable () -> Unit,
  footer: @Composable () -> Unit
) {
  Column(modifier = modifier) {
    title()

    val postCommentMut by postCellState.postComment
    val postComment = postCommentMut

    if (postComment != null && postComment.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      body()
    }

    val repliesFromCount by postCellState.repliesFromCount
    if (repliesFromCount > 0) {
      Spacer(modifier = Modifier.height(8.dp))
      footer()
    }
  }
}