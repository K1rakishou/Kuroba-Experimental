package com.github.k1rakishou.chan.ui.compose.post.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState
import com.github.k1rakishou.chan.ui.compose.post.ui.body.PostCellBodyUi
import com.github.k1rakishou.chan.ui.compose.post.ui.footer.PostCellFooterUi
import com.github.k1rakishou.chan.ui.compose.post.ui.title.PostCellTitleContainer

@Composable
fun PostCellUi(
  modifier: Modifier,
  postCellState: PostCellState,
  threadState: ThreadState
) {
  PostCellUiLayout(
    modifier = modifier,
    postCellState = postCellState,
    title = {
      PostCellTitleContainer(
        modifier = Modifier.fillMaxWidth(),
        threadState = threadState,
        postCellState = postCellState
      )
    },
    body = {
      PostCellBodyUi(
        modifier = Modifier.fillMaxWidth(),
        threadState = threadState,
        postCellState = postCellState
      )
    },
    footer = {
      PostCellFooterUi(
        modifier = Modifier.fillMaxWidth(),
        threadState = threadState,
        postCellState = postCellState
      )
    }
  )
}