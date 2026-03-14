package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.v2.KurobaSettings
import java.util.concurrent.TimeUnit

class WatcherSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources
) : SettingsScreenBuilder {

  override suspend fun build(context: Context, settingActions: SettingActions, settingsScreen: SettingsScreen) {
    with(settingsScreen) {
      addThreadWatcherGroup()
      addFilterWatcherGroup()
      addThreadDownloaderGroup()
    }
  }

  private suspend fun SettingsScreen.addThreadDownloaderGroup() {
    addGroup(
      key = "thread_downloader",
      title = appResources.string(R.string.settings_thread_downloader_group)
    ) {
      addSetting(
        SettingUiElement.Items<Long>(
          title = { appResources.string(R.string.setting_thread_downloader_update_interval) },
          description = { appResources.string(R.string.setting_thread_downloader_update_interval_description) },
          items = if (AppModuleAndroidUtils.isDevBuild) {
            THREAD_DOWNLOADER_INTERVALS
          } else {
            THREAD_DOWNLOADER_INTERVALS.drop(1)
          },
          itemNameMapper = { interval ->
            val timeoutShouldBeInMinutes = TimeUnit.MILLISECONDS.toHours(interval) <= 0
            if (timeoutShouldBeInMinutes) {
              return@Items appResources.string(
                R.string.minutes,
                TimeUnit.MILLISECONDS.toMinutes(interval)
              )
            }

            return@Items appResources.string(
              R.string.hours,
              TimeUnit.MILLISECONDS.toHours(interval)
            )
          },
          selectionType = SettingUiElement.SelectionType.Multiple("thread_downloader_intervals"),
          setting = kurobaSettings.application.threadDownloaderUpdateInterval
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_thread_downloader_media_metered_network) },
          description = { appResources.string(R.string.setting_thread_downloader_media_metered_network_description) },
          setting = kurobaSettings.application.threadDownloaderDownloadMediaOnMeteredNetwork
        )
      )
    }
  }

  private suspend fun SettingsScreen.addFilterWatcherGroup() {
    addGroup(
      key = "filter_watcher",
      title = appResources.string(R.string.settings_filter_watcher_group)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_watch_enable_filter_watcher) },
          description = { appResources.string(R.string.setting_watch_enable_filter_watcher_description) },
          setting = kurobaSettings.application.filterWatchEnabled
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_watch_filter_watcher_use_filter_pattern_for_group) },
          description = {
            appResources.string(R.string.setting_watch_filter_watcher_use_filter_pattern_for_group_description)
          },
          setting = kurobaSettings.application.filterWatchUseFilterPatternForGroup,
          dependencies = listOf(kurobaSettings.application.filterWatchEnabled)
        )
      )

      addSetting(
        SettingUiElement.Items<Long>(
          title = { appResources.string(R.string.setting_filter_watcher_update_interval) },
          description = { appResources.string(R.string.setting_filter_watcher_update_interval_description) },
          items = if (AppModuleAndroidUtils.isDevBuild) {
            FILTER_WATCHER_INTERVALS
          } else {
            FILTER_WATCHER_INTERVALS.drop(1)
          },
          itemNameMapper = { interval ->
            val timeoutShouldBeInMinutes = TimeUnit.MILLISECONDS.toHours(interval) <= 0
            if (timeoutShouldBeInMinutes) {
              return@Items appResources.string(
                R.string.minutes,
                TimeUnit.MILLISECONDS.toMinutes(interval)
              )
            }

            return@Items appResources.string(
              R.string.hours,
              TimeUnit.MILLISECONDS.toHours(interval)
            )
          },
          selectionType = SettingUiElement.SelectionType.Multiple("filter_watcher_intervals"),
          setting = kurobaSettings.application.filterWatchInterval,
          dependencies = listOf(kurobaSettings.application.filterWatchEnabled)
        )
      )
    }
  }

  private suspend fun SettingsScreen.addThreadWatcherGroup() {
    addGroup(
      key = "thread_watcher",
      title = appResources.string(R.string.settings_thread_watcher_group)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_watch_enable_thread_watcher) },
          description = { appResources.string(R.string.setting_watch_enable_thread_watcher_description) },
          setting = kurobaSettings.application.watchEnabled
        )
      )

      addSetting(
        SettingUiElement.Items<Long>(
          title = { appResources.string(R.string.setting_watch_foreground_timeout) },
          description = { appResources.string(R.string.setting_watch_foreground_timeout_description) },
          setting = kurobaSettings.application.watchForegroundInterval,
          dependencies = listOf(kurobaSettings.application.watchEnabled),
          items = THREAD_WATCHER_FOREGROUND_INTERVALS,
          itemNameMapper = { interval ->
            appResources.string(
              R.string.seconds,
              TimeUnit.MILLISECONDS.toSeconds(interval).toInt()
            )
          },
          selectionType = SettingUiElement.SelectionType.Multiple("foreground_watcher_intervals")
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_watch_enable_background) },
          description = { appResources.string(R.string.setting_watch_enable_background_description) },
          setting = kurobaSettings.application.watchBackground,
          dependencies = listOf(kurobaSettings.application.watchEnabled)
        )
      )

      addSetting(
        SettingUiElement.Items<Long>(
          title = { appResources.string(R.string.setting_watch_background_timeout) },
          description = { appResources.string(R.string.setting_watch_background_timeout_description) },
          items = run {
            if (AppModuleAndroidUtils.isDevBuild) {
              THREAD_WATCHER_BACKGROUND_INTERVALS
            } else {
              THREAD_WATCHER_BACKGROUND_INTERVALS.drop(1)
            }
          },
          selectionType = SettingUiElement.SelectionType.Multiple("background_watcher_intervals"),
          itemNameMapper = { interval ->
            val timeoutString = appResources.string(
              R.string.minutes,
              TimeUnit.MILLISECONDS.toMinutes(interval).toInt()
            )

            val testOptionThreshold = TimeUnit.MINUTES.toMillis(1).toInt()
            if (interval <= testOptionThreshold) {
              return@Items appResources.string(
                R.string.setting_background_watcher_test_option,
                timeoutString
              )
            }

            val optimalTimeoutThreshold = TimeUnit.MINUTES.toMillis(30).toInt()
            if (interval >= optimalTimeoutThreshold) {
              return@Items appResources.string(
                R.string.setting_background_watcher_optimal_option,
                timeoutString
              )
            }

            val nonOptimalTimeoutsThreshold = TimeUnit.MINUTES.toMillis(10).toInt()
            if (interval >= nonOptimalTimeoutsThreshold) {
              return@Items appResources.string(
                R.string.setting_background_watcher_non_optimal_option,
                timeoutString
              )
            }

            return@Items appResources.string(
              R.string.setting_background_watcher_very_bad_option,
              timeoutString
            )
          },
          setting = kurobaSettings.application.watchBackgroundInterval,
          dependencies = listOf(kurobaSettings.application.watchBackground)
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_reply_notifications) },
          description = { appResources.string(R.string.setting_reply_notifications_description) },
          setting = kurobaSettings.application.replyNotifications,
          dependencies = listOf(kurobaSettings.application.watchEnabled)
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_reply_notifications_use_sound) },
          setting = kurobaSettings.application.useSoundForReplyNotifications,
          dependencies = listOf(kurobaSettings.application.replyNotifications)
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_thread_page_limit_notify) },
          description = { appResources.string(R.string.setting_thread_page_limit_notify_description) },
          setting = kurobaSettings.application.watchLastPageNotify,
          dependencies = listOf(kurobaSettings.application.watchBackground)
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_thread_page_limit_notify_use_sound) },
          setting = kurobaSettings.application.useSoundForLastPageNotifications,
          dependencies = listOf(kurobaSettings.application.watchLastPageNotify)
        )
      )
    }
  }

  companion object {
    private val THREAD_WATCHER_BACKGROUND_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1),
      TimeUnit.MINUTES.toMillis(5),
      TimeUnit.MINUTES.toMillis(10),
      TimeUnit.MINUTES.toMillis(15),
      TimeUnit.MINUTES.toMillis(30),
      TimeUnit.MINUTES.toMillis(45),
      TimeUnit.MINUTES.toMillis(60),
      TimeUnit.MINUTES.toMillis(90),
      TimeUnit.MINUTES.toMillis(120)
    )

    private val THREAD_WATCHER_FOREGROUND_INTERVALS = listOf(
      TimeUnit.SECONDS.toMillis(30),
      TimeUnit.MINUTES.toMillis(1),
      TimeUnit.MINUTES.toMillis(2),
      TimeUnit.MINUTES.toMillis(5),
      TimeUnit.MINUTES.toMillis(10),
    )

    private val FILTER_WATCHER_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1),
      TimeUnit.MINUTES.toMillis(30),
      TimeUnit.HOURS.toMillis(1),
      TimeUnit.HOURS.toMillis(2),
      TimeUnit.HOURS.toMillis(4),
      TimeUnit.HOURS.toMillis(8),
      TimeUnit.HOURS.toMillis(12),
      TimeUnit.HOURS.toMillis(24)
    )

    private val THREAD_DOWNLOADER_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1),
      TimeUnit.MINUTES.toMillis(30),
      TimeUnit.MINUTES.toMillis(45),
      TimeUnit.HOURS.toMillis(1),
      TimeUnit.HOURS.toMillis(2),
      TimeUnit.HOURS.toMillis(3),
      TimeUnit.HOURS.toMillis(4),
    )
  }

}