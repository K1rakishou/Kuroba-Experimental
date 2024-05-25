package com.github.k1rakishou.chan.ui.compose.post.ui.title

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState

@Composable
internal fun PostCellTitleUi(
  modifier: Modifier,
  threadState: ThreadState,
  postCellState: PostCellState
) {
  val postTitleMut by postCellState.postTitle
  val postTitle = postTitleMut

  val postTitleFontSizeMut by threadState.postTitleFontSize.collectAsState()
  val postTitleFontSize = postTitleFontSizeMut

  val thumbnailSizeMut by threadState.thumbnailSize.collectAsState()
  val thumbnailSize = thumbnailSizeMut

  if (postTitle == null || postTitleFontSize == null) {
    val shimmerHeight = thumbnailSize ?: 32.dp

    Shimmer(
      modifier = modifier
        then Modifier.height(shimmerHeight)
    )

    return
  }

  KurobaComposeText(
    modifier = modifier,
    text = postTitle,
    fontSize = postTitleFontSize.ktu.fixedSize()
  )
}