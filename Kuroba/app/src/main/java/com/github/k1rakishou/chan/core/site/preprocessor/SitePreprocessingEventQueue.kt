package com.github.k1rakishou.chan.core.site.preprocessor

import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SitePreprocessingEventQueue {
  private val _eventQueue: MutableSharedFlow<Event> = MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)
  val eventQueue: SharedFlow<Event>
    get() = _eventQueue.asSharedFlow()

  suspend fun addEvent(event: Event) {
    _eventQueue.emit(event)
  }

  interface Event {
    val siteDescriptor: SiteDescriptor
  }
}