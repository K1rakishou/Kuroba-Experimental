package com.github.k1rakishou.chan.core.helper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ChanLoadProgressNotifier {
  private val _progressEventsFlow = MutableSharedFlow<ChanLoadProgressEvent>(
    replay = 0,
    extraBufferCapacity = 32,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val progressEventsFlow: SharedFlow<ChanLoadProgressEvent>
    get() = _progressEventsFlow.asSharedFlow()

  fun sendProgressEvent(progressEvent: ChanLoadProgressEvent) {
    _progressEventsFlow.tryEmit(progressEvent)
  }

}

sealed class ChanLoadProgressEvent {
  abstract val chanDescriptor: ChanDescriptor

  data class Begin(override val chanDescriptor: ChanDescriptor) : ChanLoadProgressEvent()
  data class Loading(override val chanDescriptor: ChanDescriptor) : ChanLoadProgressEvent()
  data class Reading(override val chanDescriptor: ChanDescriptor) : ChanLoadProgressEvent()
  data class ProcessingFilters(override val chanDescriptor: ChanDescriptor, val filtersCount: Int) : ChanLoadProgressEvent()
  data class ParsingPosts(override val chanDescriptor: ChanDescriptor, val postsToParseCount: Int) : ChanLoadProgressEvent()
  data class PersistingPosts(override val chanDescriptor: ChanDescriptor, val postsCount: Int) : ChanLoadProgressEvent()
  data class ApplyingFilters(override val chanDescriptor: ChanDescriptor, val postHidesCount: Int, val postFiltersCount: Int) : ChanLoadProgressEvent()
  data class RefreshingPosts(override val chanDescriptor: ChanDescriptor) : ChanLoadProgressEvent()
  data class End(override val chanDescriptor: ChanDescriptor) : ChanLoadProgressEvent()
}