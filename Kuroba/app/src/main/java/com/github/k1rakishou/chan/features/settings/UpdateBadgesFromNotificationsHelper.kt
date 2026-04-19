package com.github.k1rakishou.chan.features.settings

import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.ui.settings.SettingNotification
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.KurobaSettings

class UpdateBadgesFromNotificationsHelper(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val settingsNotificationManager: SettingsNotificationManager
) {
  suspend fun update(settingBadges: SnapshotStateMap<String, List<SettingUiElement.Badge>>) {
    settingsNotificationManager.dismissedNotifications.forEach { settingNotification ->
      when (settingNotification) {
        SettingNotification.ApkUpdate -> {
          dismiss(
            settingBadges = settingBadges,
            key = KurobaSettingKey.Application.AppUpdate,
            filterFunc = { badge -> badge !is SettingUiElement.Badge.NewAppUpdate }
          )
        }
        SettingNotification.MpvLibsUpdate -> {
          dismiss(
            settingBadges = settingBadges,
            key = KurobaSettingKey.Mpv.MpvLibsUpdate,
            filterFunc = { badge -> badge !is SettingUiElement.Badge.NewAppUpdate }
          )
        }
      }
    }

    settingsNotificationManager.activeNotifications.forEach { settingNotification ->
      when (settingNotification) {
        SettingNotification.ApkUpdate -> {
          show(
            settingBadges = settingBadges,
            key = KurobaSettingKey.Application.AppUpdate,
            newBadge = SettingUiElement.Badge.NewAppUpdate(
              text = appResources.string(R.string.update_available),
              description = kurobaSettings.internal.apkUpdateInfoJson.read()
                .versionName
                .takeIf { it.isNotNullNorBlank() }
            )
          )
        }
        SettingNotification.MpvLibsUpdate -> {
          show(
            settingBadges = settingBadges,
            key = KurobaSettingKey.Mpv.MpvLibsUpdate,
            newBadge = SettingUiElement.Badge.NewAppUpdate(
              text = appResources.string(R.string.update_available)
            )
          )
        }
      }
    }
  }

  private fun dismiss(
    settingBadges: SnapshotStateMap<String, List<SettingUiElement.Badge>>,
    key: KurobaSettingKey,
    filterFunc: (SettingUiElement.Badge) -> Boolean
  ) {
    val badges = settingBadges[key.raw]
      ?: emptyList()

    settingBadges[key.raw] = badges.filter(filterFunc)
  }

  private fun show(
    settingBadges: SnapshotStateMap<String, List<SettingUiElement.Badge>>,
    key: KurobaSettingKey,
    newBadge: SettingUiElement.Badge
  ) {
    val badges = settingBadges[key.raw]
      ?: emptyList()

    settingBadges[key.raw] = combineBadges(badges, listOf(newBadge))
  }

  private fun combineBadges(
    oldBadges: List<SettingUiElement.Badge>,
    newBadges: List<SettingUiElement.Badge>
  ): List<SettingUiElement.Badge> {
    if (newBadges.isEmpty()) {
      return oldBadges
    }

    val duplicateChecker = oldBadges.toHashSetBy { badge -> badge.key }

    val combinedBadges = mutableListWithCap<SettingUiElement.Badge>(oldBadges.size + newBadges.size)
    combinedBadges.addAll(oldBadges)

    for (badge in newBadges) {
      if (!duplicateChecker.add(badge.key)) {
        continue
      }

      combinedBadges += badge
    }

    return combinedBadges
  }
}