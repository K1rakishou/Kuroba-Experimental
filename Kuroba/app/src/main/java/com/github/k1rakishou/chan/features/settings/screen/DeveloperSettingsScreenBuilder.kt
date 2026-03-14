package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.SettingsScreenKey
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.v2.KurobaSettings

class DeveloperSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val themeEngine: ThemeEngine,
  private val appRestarter: AppRestarter
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildMainSettingsGroup(context, settingActions)
    }
  }

  private suspend fun SettingsScreen.buildMainSettingsGroup(
    context: Context,
    settingActions: SettingActions,
  ) {
    addGroup(
      key = "main",
      title = appResources.string(R.string.settings_developer_group)
    ) {
      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_force_low_ram_device) },
          description = { appResources.string(R.string.settings_force_low_ram_device_description) },
          setting = kurobaSettings.application.isLowRamDeviceForced,
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_check_update_apk_version_code) },
          description = { appResources.string(R.string.settings_check_update_apk_version_code_description) },
          setting = kurobaSettings.application.checkUpdateApkVersionCode
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ViewLogs",
          title = { appResources.string(R.string.settings_open_logs) },
          callback = { settingActions.pushController(LogsController(context)) }
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_verbose_logs) },
          setting = kurobaSettings.application.verboseLogs,
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "CrashApp",
          title = { appResources.string(R.string.settings_crash_app) },
          callback = { throw RuntimeException("Debug crash") }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ShowDatabaseSummary",
          title = { appResources.string(R.string.settings_database_summary) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Database, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_crash_on_safe_throw) },
          description = { appResources.string(R.string.settings_crash_on_safe_throw_description) },
          setting = kurobaSettings.application.crashOnSafeThrow
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "SimulateAppUpdated",
          title = { appResources.string(R.string.settings_simulate_app_updated) },
          description = { appResources.string(R.string.settings_simulate_app_updated_bottom) },
          callback = {
            kurobaSettings.internal.updateCheckTime.write(0L)
            kurobaSettings.internal.hasNewApkUpdate.write(false)
            appRestarter.restart()
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "SimulateAppNotUpdated",
          title = { appResources.string(R.string.settings_simulate_app_not_updated) },
          description = { appResources.string(R.string.settings_simulate_app_not_updated_bottom) },
          callback = {
            kurobaSettings.internal.updateCheckTime.write(0L)
            kurobaSettings.internal.hasNewApkUpdate.write(true)
            appRestarter.restart()
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ThemeAutoSwitcher",
          title = { appResources.string(R.string.settings_auto_theme_switcher) },
          description = {
            val status = if (themeEngine.isAutoThemeSwitcherRunning()) {
              "Running"
            } else {
              "Stopped"
            }

            return@Link appResources.string(R.string.settings_auto_theme_switcher_bottom, status)
          },
          callback = {
            if (themeEngine.isAutoThemeSwitcherRunning()) {
              themeEngine.stopAutoThemeSwitcher()
            } else {
              themeEngine.startAutoThemeSwitcher()
            }
          }
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_fun_things_are_fun) },
          setting = kurobaSettings.application.funThingsAreFun,
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_force_4chan_birthday) },
          setting = kurobaSettings.application.force4chanBirthdayMode,
          dependencies = listOf(kurobaSettings.application.funThingsAreFun),
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_force_halloween) },
          setting = kurobaSettings.application.forceHalloweenMode,
          dependencies = listOf(kurobaSettings.application.funThingsAreFun),
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_force_christmas) },
          setting = kurobaSettings.application.forceChristmasMode,
          dependencies = listOf(kurobaSettings.application.funThingsAreFun),
          requiresAppRestart = true
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_force_new_year) },
          setting = kurobaSettings.application.forceNewYearMode,
          dependencies = listOf(kurobaSettings.application.funThingsAreFun),
          requiresAppRestart = true
        )
      )
    }
  }

}