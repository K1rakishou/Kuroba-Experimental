package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaViewerScrollerHelper(
  private val chanThreadManager: ChanThreadManager
) {
  private val _mediaViewerScrollEventsFlow = MutableSharedFlow<ChanPostImage>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val mediaViewerScrollEventsFlow: SharedFlow<ChanPostImage>
    get() = _mediaViewerScrollEventsFlow.asSharedFlow()

  fun onScrolledTo(postDescriptor: PostDescriptor, mediaLocation: MediaLocation) {
    if (mediaLocation !is MediaLocation.Remote) {
      return
    }

    val chanPostImage = chanThreadManager.getPost(postDescriptor)
      ?.postImages
      ?.firstOrNull { chanPostImage -> chanPostImage.imageUrl == mediaLocation.url }

    if (chanPostImage == null) {
      return
    }

    _mediaViewerScrollEventsFlow.tryEmit(chanPostImage)
  }

}