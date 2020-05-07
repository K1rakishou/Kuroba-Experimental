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
          identifier = ThreadWatcherScreen.MainGroup.ShortPinInfo,
          topDescriptionIdFunc = { R.string.setting_bookmark_short_info },
          bottomDescriptionIdFunc = { R.string.setting_bookmark_short_info_description },
          setting = ChanSettings.shortPinInfo
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = ThreadWatcherScreen.MainGroup.ThreadWatcherTimeout,
          topDescriptionIdFunc = { R.string.setting_watch_background_timeout },
          bottomDescriptionStringFunc = { name ->
            getString(R.string.setting_watch_background_timeout_description).toString() + "\n\n" + name
          },
          items = listOf(
            TimeUnit.MINUTES.toMillis(2).toInt(),
            TimeUnit.MINUTES.toMillis(5).toInt(),
            TimeUnit.MINUTES.toMillis(10).toInt(),
            TimeUnit.MINUTES.toMillis(15).toInt(),
            TimeUnit.MINUTES.toMillis(30).toInt(),
            TimeUnit.MINUTES.toMillis(45).toInt(),
            TimeUnit.HOURS.toMillis(1).toInt(),
            TimeUnit.HOURS.toMillis(2).toInt()
          ),
          itemNameMapper = { timeout ->
            getString(R.string.minutes, TimeUnit.MILLISECONDS.toMinutes(timeout.toLong()).toInt())
          },
          setting = ChanSettings.watchBackgroundInterval
        )

        return group
      }
    )
  }


}