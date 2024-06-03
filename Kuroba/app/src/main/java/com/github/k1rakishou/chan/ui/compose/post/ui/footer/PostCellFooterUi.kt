package com.github.k1rakishou.chan.ui.compose.post.ui.footer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState

@Composable
internal fun PostCellFooterUi(
  modifier: Modifier,
  threadState: ThreadState,
  postCellState: PostCellState
) {
  val repliesFromCount by postCellState.repliesFromCount

  KurobaComposeText(
    modifier = modifier,
    text = "${repliesFromCount} Replies"
  )
}