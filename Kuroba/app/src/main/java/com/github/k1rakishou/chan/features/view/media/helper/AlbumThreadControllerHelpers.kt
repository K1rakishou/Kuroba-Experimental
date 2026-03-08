package com.github.k1rakishou.chan.features.view.media.helper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AlbumThreadControllerHelpers {
  private val _highlightPostWithImageEventsFlow = MutableSharedFlow<HighlightPostWithImageEvent>(
    extraBufferCapacity = Channel.UNLIMITED
  )
  val highlightPostWithImageEventsFlow: SharedFlow<HighlightPostWithImageEvent>
    get() = _highlightPostWithImageEventsFlow.asSharedFlow()

  fun highlightPostWithImage(chanDescriptor: ChanDescriptor, chanPostImage: ChanPostImage) {
    _highlightPostWithImageEventsFlow.tryEmit(
      HighlightPostWithImageEvent(
        chanDescriptor = chanDescriptor,
        chanPostImage = chanPostImage
      )
    )
  }

  data class HighlightPostWithImageEvent(
    val chanDescriptor: ChanDescriptor,
    val chanPostImage: ChanPostImage
  )

}