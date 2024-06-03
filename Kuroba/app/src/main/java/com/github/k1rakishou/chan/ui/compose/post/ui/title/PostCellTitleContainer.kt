package com.github.k1rakishou.chan.ui.compose.post.ui.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState

@Composable
internal fun PostCellTitleContainer(
  modifier: Modifier,
  threadState: ThreadState,
  postCellState: PostCellState
) {
  val postMultipleImagesCompactModeMut by threadState.postMultipleImagesCompactMode.collectAsState()
  val postMultipleImagesCompactMode = postMultipleImagesCompactModeMut
  if (postMultipleImagesCompactMode == null) {
    return
  }

  val thumbnailSizeMut by threadState.thumbnailSize.collectAsState()
  val thumbnailSize = thumbnailSizeMut
  if (thumbnailSize == null) {
    return
  }

  val thumbnailScalingMut by threadState.thumbnailScaling.collectAsState()
  val thumbnailScaling = thumbnailScalingMut
  if (thumbnailScaling == null) {
    return
  }

  val postMediaList by postCellState.postMediaList
  if (postMediaList.size <= 1 || postMultipleImagesCompactMode) {
    PostCellTitleUiOneOrLessImages(
      modifier = modifier,
      thumbnailSize = thumbnailSize,
      thumbnailScaling = thumbnailScaling,
      postCellState = postCellState,
      threadState = threadState,
      postMediaList = postMediaList,
      postMultipleImagesCompactMode = postMultipleImagesCompactMode
    )
  } else {
    PostCellTitleMultipleImagesContainer(
      modifier = modifier,
      thumbnailSize = thumbnailSize,
      thumbnailScaling = thumbnailScaling,
      postCellState = postCellState,
      threadState = threadState,
      postMediaList = postMediaList
    )
  }
}