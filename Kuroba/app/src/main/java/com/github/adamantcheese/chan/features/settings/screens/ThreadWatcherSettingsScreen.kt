package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.ThreadWatcherScreen
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2
import com.github.adamantcheese.chan.features.settings.setting.ListSettingV2
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import java.util.concurrent.TimeUnit

class ThreadWatcherSettingsScreen(context: Context) : BaseSettingsScreen(
  context,
  ThreadWatcherScreen,
  R.string.settings_screen_watch
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(buildMainSettingsGroup())
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = ThreadWatcherScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_watch),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.EnableThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_watcher },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_watcher_description },
          setting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ShortPinInfo,
          topDescriptionIdFunc = { R.string.setting_bookmark_short_info },
          bottomDescriptionIdFunc = { R.string.setting_bookmark_short_info_description },
          setting = ChanSettings.shortPinInfo,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.EnableBackgroundThreadWatcher,
          topDescriptionIdFunc = { R.string.setting_watch_enable_background },
          bottomDescriptionIdFunc = { R.string.setting_watch_enable_background_description },
          setting = ChanSettings.watchBackground,
          dependsOnSetting = ChanSettings.watchEnabled
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ThreadWatcherTimeout,
          topDescriptionIdFunc = { R.string.setting_watch_background_timeout },
          bottomDescriptionStringFunc = { itemName ->
            getString(R.string.setting_watch_background_timeout_description).toString() + "\n\n" + itemName
          },
          items = INTERVALS,
          itemNameMapper = { timeout ->
            val timeoutString = getString(
              R.string.minutes,
              TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt()
            )

            if (timeout == FIRST_TIMEOUT_OPTION) {
              return@createBuilder getString(
                R.string.setting_background_watcher_first_option,
                timeoutString
              )
            } else if (timeout == LAST_TIMEOUT_OPTION) {
              return@createBuilder getString(
                R.string.setting_background_watcher_last_option,
                timeoutString
              )
            }

            return@createBuilder timeoutString
          },
          setting = ChanSettings.watchBackgroundInterval,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.RemoveWatchedThreadsFromCatalog,
          topDescriptionIdFunc = { R.string.setting_remove_watched },
          setting = ChanSettings.removeWatchedFromCatalog,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.WatchLastPageNotify,
          topDescriptionIdFunc = { R.string.setting_thread_page_limit_notify },
          bottomDescriptionIdFunc = { R.string.setting_thread_page_limit_notify_description },
          setting = ChanSettings.watchLastPageNotify,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += ListSettingV2.createBuilder<String>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.WatchNotifyMode,
          topDescriptionIdFunc = { R.string.setting_watch_notify_mode },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = listOf(NOTIFY_ALL_POSTS, NOTIFY_ONLY_QUOTES),
          itemNameMapper = { item ->
            when (item) {
              NOTIFY_ALL_POSTS -> context.resources.getString(R.string.setting_watch_notify_mode_all_posts)
              NOTIFY_ONLY_QUOTES -> context.resources.getString(R.string.setting_watch_notify_mode_only_quotes)
              else -> throw IllegalArgumentException("Unknown item: ${item}")
            }
          },
          setting = ChanSettings.watchNotifyMode,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += ListSettingV2.createBuilder<String>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.WatchSound,
          topDescriptionIdFunc = { R.string.setting_watch_sound },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = listOf(NOTIFY_ALL_POSTS, NOTIFY_ONLY_QUOTES),
          itemNameMapper = { item ->
            when (item) {
              NOTIFY_ALL_POSTS -> context.resources.getString(R.string.setting_watch_notify_sound_all_posts)
              NOTIFY_ONLY_QUOTES -> context.resources.getString(R.string.setting_watch_notify_sound_only_quotes)
              else -> throw IllegalArgumentException("Unknown item: ${item}")
            }
          },
          setting = ChanSettings.watchSound,
          dependsOnSetting = ChanSettings.watchBackground
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.WatchHeadsup,
          topDescriptionIdFunc = { R.string.setting_watch_peek },
          bottomDescriptionIdFunc = { R.string.setting_watch_peek_description },
          setting = ChanSettings.watchPeek,
          dependsOnSetting = ChanSettings.watchBackground
        )

        return group
      }
    )
  }

  companion object {
    const val NOTIFY_ALL_POSTS = "all"
    const val NOTIFY_ONLY_QUOTES = "quotes"

    private val INTERVALS = listOf(
      TimeUnit.MINUTES.toMillis(15).toInt(),
      TimeUnit.MINUTES.toMillis(30).toInt(),
      TimeUnit.MINUTES.toMillis(45).toInt(),
      TimeUnit.MINUTES.toMillis(60).toInt(),
      TimeUnit.MINUTES.toMillis(90).toInt(),
      TimeUnit.MINUTES.toMillis(120).toInt()
    )

    private val FIRST_TIMEOUT_OPTION = INTERVALS.first()
    private val LAST_TIMEOUT_OPTION = INTERVALS.last()
  }
}