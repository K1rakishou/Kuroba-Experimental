package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaViewerOpenThreadHelper {

  private val _mediaViewerOpenThreadEventsFlow = MutableSharedFlow<ChanDescriptor.ThreadDescriptor>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val mediaViewerOpenThreadEventsFlow: SharedFlow<ChanDescriptor.ThreadDescriptor>
    get() = _mediaViewerOpenThreadEventsFlow.asSharedFlow()

  fun tryToOpenThread(
    chanDescriptor: ChanDescriptor?,
    postDescriptor: PostDescriptor
  ): Boolean {
    if (chanDescriptor == null) {
      return false
    }

    _mediaViewerOpenThreadEventsFlow.tryEmit(postDescriptor.threadDescriptor())
    return true
  }

}