package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.ui.settings.SettingNotification
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SettingsNotificationManager {
  private val _notificationUpdates = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val notificationUpdates: SharedFlow<Unit>
    get() = _notificationUpdates.asSharedFlow()

  private val _activeNotifications = mutableSetOf<SettingNotification>()
  val activeNotifications: Set<SettingNotification>
    get() = _activeNotifications

  private val _dismissedNotifications = mutableSetOf<SettingNotification>()
  val dismissedNotifications: Set<SettingNotification>
    get() = _dismissedNotifications

  fun notify(apkUpdate: SettingNotification) {
    _activeNotifications.add(apkUpdate)
    _dismissedNotifications.remove(apkUpdate)

    _notificationUpdates.tryEmit(Unit)
  }

  fun dismiss(apkUpdate: SettingNotification) {
    _activeNotifications.remove(apkUpdate)
    _dismissedNotifications.add(apkUpdate)

    _notificationUpdates.tryEmit(Unit)
  }

  fun count(): Int {
    return _activeNotifications.size
  }
}