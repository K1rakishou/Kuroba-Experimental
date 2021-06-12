package com.github.k1rakishou.chan.ui.helper

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class AppSettingsUpdateAppRefreshHelper {
  private val _settingsUpdatedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  val settingsUpdatedEvent: SharedFlow<Unit>
    get() = _settingsUpdatedEvent

  fun settingsUpdated() {
    _settingsUpdatedEvent.tryEmit(Unit)
  }
}