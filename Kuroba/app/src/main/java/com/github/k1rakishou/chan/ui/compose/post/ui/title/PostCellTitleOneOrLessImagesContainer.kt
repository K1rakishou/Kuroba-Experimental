package com.github.k1rakishou.chan.ui.compose.post.ui.title

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposePostImageThumbnail
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellMediaState
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.PostThumbnailAlignmentUi
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun PostCellTitleUiOneOrLessImages(
  modifier: Modifier,
  postCellState: PostCellState,
  threadState: ThreadState,
  postMediaList: ImmutableList<PostCellMediaState>,
  postMultipleImagesCompactMode: Boolean
) {
  val thumbnailSizeMut by threadState.thumbnailSize.collectAsState()
  val thumbnailSize = thumbnailSizeMut
  if (thumbnailSize == null) {
    return
  }

  val thumbnailAlignmentMut by when (postCellState.chanDescriptor) {
    is ChanDescriptor.ICatalogDescriptor -> threadState.catalogThumbnailAlignment.collectAsState()
    is ChanDescriptor.ThreadDescriptor -> threadState.threadThumbnailAlignment.collectAsState()
  }
  val thumbnailAlignment = thumbnailAlignmentMut
  if (thumbnailAlignment == null) {
    return
  }

  val postCellMediaState = postMediaList.firstOrNull()

  val thumbnailContentMovable: @Composable (Boolean) -> Unit = remember(
    postCellMediaState,
    postCellState,
    threadState,
    thumbnailSize,
    postMediaList.size,
    postMultipleImagesCompactMode
  ) {
    movableContentOf { addSpacerToLeftSide: Boolean ->
      Thumbnail(
        postCellMediaState = postCellMediaState,
        postCellState = postCellState,
        threadState = threadState,
        thumbnailSize = thumbnailSize,
        postMediaListSize = postMediaList.size,
        addSpacerToLeftSide = addSpacerToLeftSide,
        postMultipleImagesCompactMode = postMultipleImagesCompactMode
      )
    }
  }

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (thumbnailAlignment == PostThumbnailAlignmentUi.Left) {
      thumbnailContentMovable(false)
    }

    PostCellTitleUi(
      modifier = Modifier.weight(1f),
      threadState = threadState,
      postCellState = postCellState
    )

    if (thumbnailAlignment == PostThumbnailAlignmentUi.Right) {
      thumbnailContentMovable(true)
    }
  }
}

@Composable
private fun Thumbnail(
  postCellMediaState: PostCellMediaState?,
  postCellState: PostCellState,
  threadState: ThreadState,
  thumbnailSize: Dp,
  postMediaListSize: Int,
  addSpacerToLeftSide: Boolean,
  postMultipleImagesCompactMode: Boolean
) {
  if (postCellMediaState == null) {
    return
  }

  val spacerWidth = 8.dp

  val requestProvider = remember(key1 = postCellMediaState) {
    return@remember getImageLoaderRequestProvider(
      chanDescriptor = postCellState.chanDescriptor,
      postCellMediaState = postCellMediaState
    )
  }

  Row {
    if (addSpacerToLeftSide) {
      Spacer(modifier = Modifier.width(spacerWidth))
    }

    Box {
      KurobaComposePostImageThumbnail(
        modifier = Modifier.size(thumbnailSize),
        controllerKey = threadState.controllerKey,
        postImageThumbnailKey = postCellMediaState.postCellMediaKey,
        requestProvider = requestProvider,
        mediaType = postCellMediaState.kurobaMediaType,
        onClick = { postImageThumbnailKey -> threadState.onPostImageClicked(postImageThumbnailKey) },
        onLongClick = { postImageThumbnailKey -> threadState.onPostImageLongClicked(postImageThumbnailKey) }
      )

      if (postMultipleImagesCompactMode && postMediaListSize > 1) {
        // TODO: compose post cells. Display overlay that shows how many images were omitted.
      }
    }

    if (!addSpacerToLeftSide) {
      Spacer(modifier = Modifier.width(spacerWidth))
    }
  }
}