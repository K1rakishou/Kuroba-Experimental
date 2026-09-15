package com.github.k1rakishou.chan.core.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The notifications permission (Android 13+) is only requested when the user enables a setting which needs it instead
 * of on every app start. When the permission is revoked, the settings that need it are disabled.
 * */
class NotificationsPermissionHelper(
  private val context: Context,
  private val kurobaSettings: KurobaSettings,
  private val runtimePermissionsHelper: RuntimePermissionsHelper,
  private val dialogFactory: DialogFactory
) {

  private val settingsRequiringNotifications: List<SettingRequiringNotifications>
    get() = listOf(
      SettingRequiringNotifications(
        setting = kurobaSettings.application.watchEnabled,
        titleId = R.string.setting_watch_enable_thread_watcher
      ),
      SettingRequiringNotifications(
        setting = kurobaSettings.application.watchBackground,
        titleId = R.string.setting_watch_enable_background
      ),
      SettingRequiringNotifications(
        setting = kurobaSettings.application.replyNotifications,
        titleId = R.string.setting_reply_notifications
      ),
      SettingRequiringNotifications(
        setting = kurobaSettings.application.watchLastPageNotify,
        titleId = R.string.setting_thread_page_limit_notify
      ),
    )

  fun hasNotificationsPermission(): Boolean {
    return AppModuleAndroidUtils.hasPostNotificationsPermission(context)
  }

  fun requiresNotificationsPermission(setting: KurobaBooleanSetting): Boolean {
    return settingsRequiringNotifications.any { settingRequiringNotifications ->
      settingRequiringNotifications.setting.key == setting.key
    }
  }

  /**
   * @return true when notifications can be shown (the permission was already granted, has just been granted or it's
   * not needed on this Android version).
   * */
  @SuppressLint("InlinedApi")
  suspend fun requestNotificationsPermissionIfNeeded(): Boolean {
    BackgroundUtils.ensureMainThread()

    if (hasNotificationsPermission()) {
      return true
    }

    val granted = suspendCancellableCoroutine { continuation ->
      val requested = runtimePermissionsHelper.requestPermission(Manifest.permission.POST_NOTIFICATIONS) { granted ->
        if (continuation.isActive) {
          continuation.resume(granted)
        }
      }

      if (!requested) {
        // Another permission request is already in progress
        continuation.resume(false)
      }
    }

    Logger.d(TAG, "requestNotificationsPermissionIfNeeded() granted: ${granted}")

    if (!granted) {
      showPermissionDeniedDialog()
    }

    return granted
  }

  /**
   * Disables all the settings that require notifications when the permission is not granted (e.g. it was revoked by
   * the user in the Android settings).
   *
   * @return titles of the settings that were disabled.
   * */
  suspend fun disableSettingsRequiringNotificationsIfPermissionMissing(): List<String> {
    if (hasNotificationsPermission()) {
      return emptyList()
    }

    val disabledSettingTitles = mutableListOf<String>()

    for (settingRequiringNotifications in settingsRequiringNotifications) {
      if (!settingRequiringNotifications.setting.read()) {
        continue
      }

      settingRequiringNotifications.setting.write(false)
      disabledSettingTitles += context.getString(settingRequiringNotifications.titleId)
    }

    if (disabledSettingTitles.isNotEmpty()) {
      Logger.d(TAG, "disableSettingsRequiringNotificationsIfPermissionMissing() disabled: ${disabledSettingTitles}")
    }

    return disabledSettingTitles
  }

  private fun showPermissionDeniedDialog() {
    val params = KurobaComposeDialogController.confirmationDialog(
      title = KurobaComposeDialogController.Text.Id(R.string.notifications_permission_required_title),
      description = KurobaComposeDialogController.Text.Id(R.string.notifications_permission_required_description),
      negativeButton = KurobaComposeDialogController.closeButton(),
      positionButton = KurobaComposeDialogController.PositiveDialogButton(
        buttonText = R.string.permission_app_settings,
        onClick = {
          val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            ("package:" + context.packageName).toUri()
          )

          AppModuleAndroidUtils.openIntent(intent)
        }
      )
    )

    dialogFactory.showDialog(
      context = context,
      params = params
    )
  }

  private class SettingRequiringNotifications(
    val setting: KurobaBooleanSetting,
    @field:StringRes val titleId: Int
  )

  companion object {
    private const val TAG = "NotificationsPermissionHelper"
  }
}
