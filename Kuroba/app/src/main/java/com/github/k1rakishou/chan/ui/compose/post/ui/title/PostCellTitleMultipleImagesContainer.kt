package com.github.k1rakishou.chan.ui.compose.post.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.Shimmer
import com.github.k1rakishou.chan.ui.compose.components.KurobaComposeText
import com.github.k1rakishou.chan.ui.compose.components.kurobaClickable
import com.github.k1rakishou.chan.ui.compose.image.KurobaComposePostImageThumbnail
import com.github.k1rakishou.chan.ui.compose.ktu
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellMediaState
import com.github.k1rakishou.chan.ui.compose.post.state.PostCellState
import com.github.k1rakishou.chan.ui.compose.post.state.ThreadState
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun PostCellTitleMultipleImagesContainer(
  modifier: Modifier,
  postCellState: PostCellState,
  threadState: ThreadState,
  postMediaList: ImmutableList<PostCellMediaState>
) {
  val thumbnailSizeMut by threadState.thumbnailSize.collectAsState()
  val thumbnailSize = thumbnailSizeMut
  if (thumbnailSize == null) {
    return
  }

  Column(modifier = modifier) {
    PostCellTitleUi(
      modifier = Modifier.fillMaxWidth(),
      threadState = threadState,
      postCellState = postCellState
    )

    if (postMediaList.isNotEmpty()) {
      Spacer(modifier = Modifier.height(4.dp))

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        for (postCellMediaState in postMediaList) {
          Thumbnail(
            modifier = Modifier
              .kurobaClickable(
                bounded = true,
                onClick = { threadState.onPostImageClicked(postCellMediaState.postCellMediaKey) },
                onLongClick = { threadState.onPostImageLongClicked(postCellMediaState.postCellMediaKey) }
              ),
            postCellMediaState = postCellMediaState,
            postCellState = postCellState,
            threadState = threadState,
            thumbnailSize = thumbnailSize
          )
        }
      }
    }
  }
}

@Composable
private fun Thumbnail(
  modifier: Modifier,
  postCellMediaState: PostCellMediaState,
  postCellState: PostCellState,
  threadState: ThreadState,
  thumbnailSize: Dp
) {
  val requestProvider = remember(key1 = postCellMediaState) {
    return@remember getImageLoaderRequestProvider(
      chanDescriptor = postCellState.chanDescriptor,
      postCellMediaState = postCellMediaState
    )
  }

  val mediaExtensionMut by postCellMediaState.mediaExtensionState
  val mediaExtension = mediaExtensionMut
  val mediaDimensionsMut by postCellMediaState.mediaDimensionsState
  val mediaDimensions = mediaDimensionsMut
  val mediaSizeMut by postCellMediaState.mediaSizeState
  val mediaSize = mediaSizeMut

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    KurobaComposePostImageThumbnail(
      modifier = Modifier.size(thumbnailSize),
      controllerKey = threadState.controllerKey,
      postImageThumbnailKey = postCellMediaState.postCellMediaKey,
      requestProvider = requestProvider,
      mediaType = postCellMediaState.kurobaMediaType,
      onClick = null,
      onLongClick = null
    )

    Spacer(modifier = Modifier.height(4.dp))

    if (mediaExtension != null && mediaExtension.text.isNotEmpty()) {
      KurobaComposeText(
        text = mediaExtension,
        fontSize = 10.ktu.fixedSize()
      )
    } else if (mediaExtension == null) {
      Shimmer(modifier = Modifier.weight(1f))
    }

    if (mediaDimensions != null && mediaDimensions.text.isNotEmpty()) {
      KurobaComposeText(
        text = mediaDimensions,
        fontSize = 10.ktu.fixedSize()
      )
    } else if (mediaDimensions == null) {
      Shimmer(modifier = Modifier.weight(1f))
    }

    if (mediaSize != null && mediaSize.text.isNotEmpty()) {
      KurobaComposeText(
        text = mediaSize,
        fontSize = 10.ktu.fixedSize()
      )
    } else if (mediaSize == null) {
      Shimmer(modifier = Modifier.weight(1f))
    }
  }
}