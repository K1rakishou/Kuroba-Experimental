package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import android.text.SpannableString
import android.text.util.Linkify
import androidx.core.text.util.LinkifyCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.watcher.BookmarkForegroundWatcher
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.WatcherScreen
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.PhoneWithBackgroundLimitationsHelper
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.persist_state.PersistableChanState
import java.util.concurrent.TimeUnit


class WatcherSettingsScreen(
  context: Context,
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val themeEngine: ThemeEngine,
  private val dialogFactory: DialogFactory
) : BaseSettingsScreen(
  context,
  WatcherScreen,
  R.string.settings_screen_watch
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildThreadWatcherSettingsGroup(),
      buildFilterWatcherSettingsGroup(),
      buildThreadDownloaderSettingsGroup()
    )
  }

  private fun buildThreadDownloaderSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = WatcherScreen.ThreadDownloaderGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_thread_downloader_group),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = WatcherScreen.ThreadDownloaderGroup.ThreadDownloaderUpdateInterval,
          topDescriptionIdFunc = { R.string.setting_thread_downloader_update_interval },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_thread_downloader_update_interval_description).toString() + "\n\n" + itemName
          },
          items = kotlin.run {
            return@run if (AppModuleAndroidUtils.isDevBuild()) {
              THREAD_DOWNLOADER_INTERVALS
            } else {
              THREAD_DOWNLOADER_INTERVALS.drop(1)
            }
          },
          itemNameMapper = { timeout ->
            return@createBuilder kotlin.run {
              val timeoutShouldBeInMinutes = TimeUnit.MILLISECONDS.toHours(timeout.toLong()).toInt() <= 0
              if (timeoutShouldBeInMinutes) {
                return@run getString(
                  R.string.minutes,
                  TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt()
                )
              }

              return@run getString(
                R.string.hours,
                TimeUnit.MILLISECONDS.toHours(timeout.toLong()).toInt()
              )
            }
          },
          setting = ChanSettings.threadDownloaderUpdateInterval
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadDownloaderGroup.ThreadDownloaderDownloadMediaOnMeteredNetwork,
          topDescriptionIdFunc = { R.string.setting_thread_downloader_media_metered_network },
          bottomDescriptionIdFunc = { R.string.setting_thread_downloader_media_metered_network_description },
          setting = ChanSettings.threadDownloaderDownloadMediaOnMeteredNetwork
        )

        group
      }
    )
  }

  private fun buildFilterWatcherSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = WatcherScreen.FilterWatcherGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_filter_watcher_group),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.FilterWatcherGroup.EnableFilterWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_filter_watcher },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_filter_watcher_description },
          setting = ChanSettings.filterWatchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.FilterWatcherGroup.FilterWatcherUseFilterPatternForGroup,
          topDescriptionIdFunc = { R.string.setting_watch_filter_watcher_use_filter_pattern_for_group },
          bottomDescriptionIdFunc = { R.string.setting_watch_filter_watcher_use_filter_pattern_for_group_description },
          setting = ChanSettings.filterWatchUseFilterPatternForGroup,
          dependsOnSetting = ChanSettings.filterWatchEnabled
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = WatcherScreen.FilterWatcherGroup.FilterWatcherUpdateInterval,
          topDescriptionIdFunc = { R.string.setting_filter_watcher_update_interval },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_filter_watcher_update_interval_description).toString() + "\n\n" + itemName
          },
          items = kotlin.run {
            return@run if (AppModuleAndroidUtils.isDevBuild()) {
              FILTER_WATCHER_INTERVALS
            } else {
              FILTER_WATCHER_INTERVALS.drop(1)
            }
          },
          itemNameMapper = { timeout ->
            return@createBuilder kotlin.run {
              val timeoutShouldBeInMinutes = TimeUnit.MILLISECONDS.toHours(timeout.toLong()).toInt() <= 0
              if (timeoutShouldBeInMinutes) {
                return@run getString(
                  R.string.minutes,
                  TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt()
                )
              }

              return@run getString(
                R.string.hours,
                TimeUnit.MILLISECONDS.toHours(timeout.toLong()).toInt()
              )
            }
          },
          setting = ChanSettings.filterWatchInterval,
          dependsOnSetting = ChanSettings.filterWatchEnabled
        )

        group
      }
    )
  }

  private fun buildThreadWatcherSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = WatcherScreen.ThreadWatcherGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_thread_watcher_group),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.EnableThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_thread_watcher },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_thread_watcher_description },
          setting = ChanSettings.watchEnabled
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.ThreadWatcherForegroundUpdateInterval,
          topDescriptionIdFunc = { R.string.setting_watch_foreground_timeout },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_watch_foreground_timeout_description).toString() + "\n\n" + itemName
          },
          items = THREAD_WATCHER_FOREGROUND_INTERVALS,
          itemNameMapper = { timeout ->
            return@createBuilder getString(
              R.string.seconds,
              TimeUnit.MILLISECONDS.toSeconds(timeout.toLong()).toInt()
            )
          },
          setting = ChanSettings.watchForegroundInterval,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.AdaptiveForegroundWatcherInterval,
          topDescriptionIdFunc = { R.string.setting_watch_foreground_adaptive_timer },
          bottomDescriptionStringFunc = {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(
              BookmarkForegroundWatcher.ADDITIONAL_INTERVAL_INCREMENT_MS
            )
            return@createBuilder getString(
              R.string.setting_watch_foreground_adaptive_timer_description,
              seconds
            )
          },
          setting = ChanSettings.watchForegroundAdaptiveInterval,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.EnableBackgroundThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_background },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_background_description },
          checkChangedCallback = { checked ->
            showShittyPhonesBackgroundLimitationsExplanationDialog(
              checked
            )
          },
          setting = ChanSettings.watchBackground,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.ThreadWatcherBackgroundUpdateInterval,
          topDescriptionIdFunc = { R.string.setting_watch_background_timeout },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_watch_background_timeout_description).toString() + "\n\n" + itemName
          },
          items = kotlin.run {
            if (AppModuleAndroidUtils.isDevBuild()) {
              THREAD_WATCHER_BACKGROUND_INTERVALS
            } else {
              THREAD_WATCHER_BACKGROUND_INTERVALS.drop(1)
            }
          },
          itemNameMapper = { timeout ->
            val timeoutString = getString(
              R.string.minutes,
              TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt()
            )

            val testOptionThreshold = TimeUnit.MINUTES.toMillis(1).toInt()
            if (timeout <= testOptionThreshold) {
              return@createBuilder getString(
                R.string.setting_background_watcher_test_option,
                timeoutString
              )
            }

            val optimalTimeoutThreshold = TimeUnit.MINUTES.toMillis(30).toInt()
            if (timeout >= optimalTimeoutThreshold) {
              return@createBuilder getString(
                R.string.setting_background_watcher_optimal_option,
                timeoutString
              )
            }

            val nonOptimalTimeoutsThreshold = TimeUnit.MINUTES.toMillis(10).toInt()
            if (timeout >= nonOptimalTimeoutsThreshold) {
              return@createBuilder getString(
                R.string.setting_background_watcher_non_optimal_option,
                timeoutString
              )
            }

            return@createBuilder getString(
              R.string.setting_background_watcher_very_bad_option,
              timeoutString
            )
          },
          setting = ChanSettings.watchBackgroundInterval,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.ReplyNotifications,
          topDescriptionIdFunc = { R.string.setting_reply_notifications },
          bottomDescriptionIdFunc = { R.string.setting_reply_notifications_description },
          setting = ChanSettings.replyNotifications,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.UseSoundForReplyNotifications,
          topDescriptionIdFunc = { R.string.setting_reply_notifications_use_sound },
          setting = ChanSettings.useSoundForReplyNotifications,
          dependsOnSetting = ChanSettings.replyNotifications
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.WatchLastPageNotify,
          topDescriptionIdFunc = { R.string.setting_thread_page_limit_notify },
          bottomDescriptionIdFunc = { R.string.setting_thread_page_limit_notify_description },
          setting = ChanSettings.watchLastPageNotify,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = WatcherScreen.ThreadWatcherGroup.UseSoundForLastPageNotifications,
          topDescriptionIdFunc = { R.string.setting_thread_page_limit_notify_use_sound },
          setting = ChanSettings.useSoundForLastPageNotifications,
          dependsOnSetting = ChanSettings.watchLastPageNotify
        )

        group
      }
    )
  }

  private fun showShittyPhonesBackgroundLimitationsExplanationDialog(checked: Boolean) {
    if (!PhoneWithBackgroundLimitationsHelper.isPhoneWithPossibleBackgroundLimitations()) {
      return
    }

    if (!checked) {
      return
    }

    if (PersistableChanState.shittyPhonesBackgroundLimitationsExplanationDialogShown.get()) {
      return
    }

    if (!applicationVisibilityManager.isAppInForeground()) {
      return
    }

    val descriptionText = SpannableString(
      context.getString(
        R.string.setting_watch_background_limitations_dialog_description,
        PhoneWithBackgroundLimitationsHelper.getFormattedLink()
      )
    )

    LinkifyCompat.addLinks(descriptionText, Linkify.WEB_URLS)

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.setting_watch_background_limitations_dialog_title,
      descriptionText = descriptionText
    )

    PersistableChanState.shittyPhonesBackgroundLimitationsExplanationDialogShown.set(true)
  }

  companion object {
    private val THREAD_WATCHER_BACKGROUND_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1).toInt(),
      TimeUnit.MINUTES.toMillis(5).toInt(),
      TimeUnit.MINUTES.toMillis(10).toInt(),
      TimeUnit.MINUTES.toMillis(15).toInt(),
      TimeUnit.MINUTES.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(45).toInt(),
      TimeUnit.MINUTES.toMillis(60).toInt(),
      TimeUnit.MINUTES.toMillis(90).toInt(),
      TimeUnit.MINUTES.toMillis(120).toInt()
    )

    private val THREAD_WATCHER_FOREGROUND_INTERVALS = listOf(
      TimeUnit.SECONDS.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(1).toInt(),
      TimeUnit.MINUTES.toMillis(2).toInt(),
      TimeUnit.MINUTES.toMillis(5).toInt(),
      TimeUnit.MINUTES.toMillis(10).toInt(),
    )

    private val FILTER_WATCHER_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1).toInt(),
      TimeUnit.MINUTES.toMillis(30).toInt(),
      TimeUnit.HOURS.toMillis(1).toInt(),
      TimeUnit.HOURS.toMillis(2).toInt(),
      TimeUnit.HOURS.toMillis(4).toInt(),
      TimeUnit.HOURS.toMillis(8).toInt(),
      TimeUnit.HOURS.toMillis(12).toInt(),
      TimeUnit.HOURS.toMillis(24).toInt()
    )

    private val THREAD_DOWNLOADER_INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(1).toInt(),
      TimeUnit.MINUTES.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(45).toInt(),
      TimeUnit.HOURS.toMillis(1).toInt(),
      TimeUnit.HOURS.toMillis(2).toInt(),
      TimeUnit.HOURS.toMillis(3).toInt(),
      TimeUnit.HOURS.toMillis(4).toInt(),
    )
  }
}