package com.github.k1rakishou.chan.features.thread_downloading

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThreadDownloadProgressNotifier {
  private val progressStorage = mutableMapOf<ChanDescriptor.ThreadDescriptor, MutableStateFlow<Event>>()

  fun listenForProgress(threadDescriptor: ChanDescriptor.ThreadDescriptor): StateFlow<Event> {
    return synchronized(progressStorage) {
      if (!progressStorage.containsKey(threadDescriptor)) {
        progressStorage[threadDescriptor] = MutableStateFlow(Event.Empty)
      }

      return@synchronized progressStorage[threadDescriptor]!!
    }
  }

  fun notifyProgressEvent(threadDescriptor: ChanDescriptor.ThreadDescriptor, event: Event) {
    synchronized(progressStorage) {
      if (!progressStorage.containsKey(threadDescriptor)) {
        progressStorage[threadDescriptor] = MutableStateFlow(Event.Empty)
      }

      progressStorage[threadDescriptor]!!.tryEmit(event)
    }
  }

  sealed class Event {
    object Empty : Event()
    class Progress(val percent: Float) : Event()
  }
}