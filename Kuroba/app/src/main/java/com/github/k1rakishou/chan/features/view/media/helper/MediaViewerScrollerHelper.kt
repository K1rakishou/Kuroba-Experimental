package com.github.k1rakishou.chan.features.view.media.helper

import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaViewerScrollerHelper(
  private val chanThreadManager: ChanThreadManager
) {
  private val _mediaViewerScrollEventsFlow = MutableSharedFlow<ScrollToImageEvent>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val mediaViewerScrollEventsFlow: SharedFlow<ScrollToImageEvent>
    get() = _mediaViewerScrollEventsFlow.asSharedFlow()

  fun onScrolledTo(
    chanDescriptor: ChanDescriptor?,
    postDescriptor: PostDescriptor,
    mediaLocation: MediaLocation
  ) {
    if (chanDescriptor == null || mediaLocation !is MediaLocation.Remote) {
      return
    }

    val chanPostImage = chanThreadManager.getPost(postDescriptor)
      ?.postImages
      ?.firstOrNull { chanPostImage -> chanPostImage.imageUrl == mediaLocation.url }

    if (chanPostImage == null) {
      return
    }

    _mediaViewerScrollEventsFlow.tryEmit(ScrollToImageEvent(chanDescriptor, chanPostImage))
  }

  data class ScrollToImageEvent(
    val chanDescriptor: ChanDescriptor,
    val chanPostImage: ChanPostImage
  )

}