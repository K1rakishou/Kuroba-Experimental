package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.core_logger.Logger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor

class SettingsNotificationManager {
  @GuardedBy("this")
  private val notifications: MutableSet<SettingNotificationType> = mutableSetOf()

  /**
   * A reactive stream that is being used to notify observers about [notifications] changes
   * */
  private val activeNotificationsSubject = BehaviorProcessor.createDefault(Unit)

  @Synchronized
  fun onThemeChanged() {
    activeNotificationsSubject.onNext(Unit)
  }

  /**
   * If [notifications] doesn't contain [notificationType] yet, then notifies
   * all observers that there is a new notification
   * */
  @Synchronized
  fun notify(notificationType: SettingNotificationType) {
    if (notifications.add(notificationType)) {
      Logger.d(TAG, "Added ${notificationType.name} notification")
      activeNotificationsSubject.onNext(Unit)
    }
  }

  @Synchronized
  fun count(): Int = notifications.size

  @Synchronized
  fun contains(notificationType: SettingNotificationType?): Boolean {
    if (notificationType == null) {
      return false
    }

    return notifications.contains(notificationType)
  }

  /**
   * If [notifications] contains [notificationType], then notifies all observers that this
   * notification has been canceled
   * */
  @Synchronized
  fun cancel(notificationType: SettingNotificationType) {
    if (notifications.remove(notificationType)) {
      Logger.d(TAG, "Removed ${notificationType.name} notification")
      activeNotificationsSubject.onNext(Unit)
    }
  }

  /**
   * Use this to observe current notification state. Duplicates checks and everything else is done
   * internally so you don't have to worry that you will get the same state twice. All updates
   * come on main thread so there is no need to worry about that as well.
   * */
  fun listenForNotificationUpdates(): Flowable<Unit> = activeNotificationsSubject
    .onBackpressureBuffer()
    .observeOn(AndroidSchedulers.mainThread())
    .doOnError { error -> Logger.e(TAG, "listenForNotificationUpdates error", error) }
    .hide()

  companion object {
    private const val TAG = "SettingsNotificationManager"
  }
}