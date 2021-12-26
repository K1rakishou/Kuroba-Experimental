package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaViewerGoToPostHelper(
  private val chanThreadManager: ChanThreadManager
) {

  private val _mediaViewerGoToPostEventsFlow = MutableSharedFlow<PostDescriptor>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val mediaViewerGoToPostEventsFlow: SharedFlow<PostDescriptor>
    get() = _mediaViewerGoToPostEventsFlow.asSharedFlow()

  fun tryGoToPost(postDescriptor: PostDescriptor): Boolean {
    if (chanThreadManager.getPost(postDescriptor) == null) {
      return false
    }

    _mediaViewerGoToPostEventsFlow.tryEmit(postDescriptor)
    return true
  }
}