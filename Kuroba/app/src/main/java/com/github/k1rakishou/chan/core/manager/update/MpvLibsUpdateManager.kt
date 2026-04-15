package com.github.k1rakishou.chan.core.manager.update

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.usecase.MpvNativeLibrariesUseCase
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.settings.SettingNotification
import com.github.k1rakishou.chan.utils.NotificationConstants
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import com.google.errorprone.annotations.concurrent.GuardedBy
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Duration.Companion.days

class MpvLibsUpdateManager(
  private val appResources: AppResources,
  private val kurobaSettings: KurobaSettings,
  private val settingsNotificationManager: SettingsNotificationManager,
  private val kurobaSystemNotifications: KurobaSystemNotifications,
  private val mpvNativeLibrariesUseCase: MpvNativeLibrariesUseCase
) {
  @GuardedBy("this")
  private var _lastCheckResult: MpvNativeLibrariesUseCase.MpvVersionInfo? = null
    set(value) = synchronized(this) { field = value }

  val lastCheckResult: MpvNativeLibrariesUseCase.MpvVersionInfo?
    get() = synchronized(this) { _lastCheckResult }

  suspend fun check(forced: Boolean): Boolean? {
    if (!kurobaSettings.application.useMpvVideoPlayer.read()) {
      return null
    }

    val currentTime = System.currentTimeMillis()
    val lastCheckTime = kurobaSettings.mpv.lastMpvLibsUpdateCheckTime.read()

    if (!forced && (lastCheckTime + UPDATE_CHECK_INTERVAL.inWholeMilliseconds) >= currentTime) {
      Logger.debug(TAG) {
        "Skipping because the check was done not long ago " +
          "(lastCheckTime: ${lastCheckTime}, currentTime: ${currentTime})"
      }

      return null
    }

    val mpvVersionInfo = when (val updateResult = mpvNativeLibrariesUseCase.checkUpdate()) {
      is ModularResult.Error<*> -> {
        Logger.error(TAG) { "Failed to check for MPV libs update ${updateResult.error.errorMessageOrClassName()}" }
        return null
      }
      is ModularResult.Value<MpvNativeLibrariesUseCase.MpvVersionInfo> -> updateResult.value
    }

    _lastCheckResult = mpvVersionInfo

    kurobaSettings.mpv.lastMpvLibsUpdateCheckTime.write(System.currentTimeMillis())

    if (mpvVersionInfo.usingLatestVersion()) {
      Logger.debug(TAG) { "No new version available" }
      return false
    }

    Logger.debug(TAG) {
      "New version of MPV libs is available. " +
        "newVersion: ${mpvVersionInfo.supportedVersionFormatted()}, " +
        "current: ${mpvVersionInfo.currentVersionFormatted(appResources)}"
    }

    settingsNotificationManager.notify(SettingNotification.MpvLibsUpdate)

    kurobaSystemNotifications.showNotification(
      notificationData = KurobaSystemNotifications.NotificationData(
        id = NotificationConstants.Generic.Ids.NewMpvListVersionAvailable.name,
        priority = KurobaSystemNotifications.NotificationData.Priority.High,
        largeIcon = KurobaSystemNotifications.NotificationData.LargeIcon.RemoteUrl(
          url = (AppConstants.RESOURCES_ENDPOINT + "ic_launcher_release_round.png").toHttpUrl()
        ),
        autoCancel = false,
        style = KurobaSystemNotifications.NotificationData.Style.Default(
          title = appResources.string(R.string.mpv_libraries_update_title),
          content = appResources.string(
            R.string.mpv_libraries_update_description,
            mpvVersionInfo.currentVersionFormatted(appResources),
            mpvVersionInfo.supportedVersionFormatted()
          )
        ),
      )
    )

    return true
  }

  companion object {
    private const val TAG = "MpvLibsUpdateManager"

    private val UPDATE_CHECK_INTERVAL = 7.days
  }
}