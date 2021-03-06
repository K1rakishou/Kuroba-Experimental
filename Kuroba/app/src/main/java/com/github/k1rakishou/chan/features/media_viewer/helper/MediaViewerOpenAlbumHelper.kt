package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaViewerOpenAlbumHelper(
  private val chanThreadManager: ChanThreadManager
) {

  private val _mediaViewerOpenAlbumEventsFlow = MutableSharedFlow<OpenAlbumEvent>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val mediaViewerOpenAlbumEventsFlow: SharedFlow<OpenAlbumEvent>
    get() = _mediaViewerOpenAlbumEventsFlow.asSharedFlow()

  fun openAlbum(
    chanDescriptor: ChanDescriptor?,
    postDescriptor: PostDescriptor,
    mediaLocation: MediaLocation
  ): Boolean {
    if (chanDescriptor == null || mediaLocation !is MediaLocation.Remote) {
      return false
    }

    val chanPostImage = chanThreadManager.getPost(postDescriptor)
      ?.postImages
      ?.firstOrNull { chanPostImage -> chanPostImage.imageUrl == mediaLocation.url }

    if (chanPostImage == null) {
      return false
    }

    _mediaViewerOpenAlbumEventsFlow.tryEmit(OpenAlbumEvent(chanDescriptor, chanPostImage))
    return true
  }

  data class OpenAlbumEvent(
    val chanDescriptor: ChanDescriptor,
    val chanPostImage: ChanPostImage
  )

}