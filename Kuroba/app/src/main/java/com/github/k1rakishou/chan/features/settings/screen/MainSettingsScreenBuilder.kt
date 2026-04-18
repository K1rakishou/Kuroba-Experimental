package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.update.KurobaAppUpdateManager
import com.github.k1rakishou.chan.features.changelog.ChangelogController
import com.github.k1rakishou.chan.features.filters.FiltersController
import com.github.k1rakishou.chan.features.report.bugs.ReportIssueController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.SettingsScreenKey
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.features.setup.site.setup.SitesSetupController
import com.github.k1rakishou.chan.ui.controller.LicensesController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.KurobaSettings
import java.util.Locale

class MainSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val chanFilterManager: ChanFilterManager,
  private val siteManager: SiteManager,
  private val kurobaAppUpdateManager: KurobaAppUpdateManager
) : SettingsScreenBuilder {

  override suspend fun build(context: Context, settingActions: SettingActions, settingsScreen: SettingsScreen) {
    with(settingsScreen) {
      addMainGroup(settingActions, context)
      addAboutAppGroup(settingActions, context)
    }
  }

  private suspend fun SettingsScreen.addMainGroup(
    settingActions: SettingActions,
    context: Context
  ) {
    addGroup(
      key = "main",
      title = appResources.string(R.string.main_settings_group)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "ThreadAndFilterWatcher",
          title = { appResources.string(R.string.settings_watch) },
          description = { appResources.string(R.string.settings_watch_summary_enabled) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Watchers, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "SitesSetup",
          title = { appResources.string(R.string.settings_sites) },
          description = {
            val sitesCount = siteManager.activeSiteCount()
            appResources.quantityString(R.plurals.site, sitesCount, sitesCount)
          },
          callback = { settingActions.pushController(SitesSetupController(context)) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Appearance",
          title = { appResources.string(R.string.settings_appearance) },
          description = { appResources.string(R.string.settings_appearance_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Appearance, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Behavior",
          title = { appResources.string(R.string.settings_behavior) },
          description = { appResources.string(R.string.settings_behavior_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Behavior, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Media",
          title = { appResources.string(R.string.settings_media) },
          description = { appResources.string(R.string.settings_media_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Media, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ImportExport",
          title = { appResources.string(R.string.settings_import_export) },
          description = { appResources.string(R.string.settings_import_export_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.ImportExport, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Filters",
          title = { appResources.string(R.string.settings_filters) },
          description = {
            val filtersCount = chanFilterManager.filtersCount()
            appResources.quantityString(R.plurals.filter, filtersCount, filtersCount)
          },
          callback = { settingActions.pushController(FiltersController(context)) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Security",
          title = { appResources.string(R.string.settings_security) },
          description = { appResources.string(R.string.settings_security_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Security, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Caching",
          title = { appResources.string(R.string.settings_caching) },
          description = { appResources.string(R.string.settings_caching_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Caching, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Plugins",
          title = { appResources.string(R.string.settings_plugins) },
          description = { appResources.string(R.string.settings_plugins_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Plugins, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "CaptchaSolvers",
          enabled = false,
          title = { appResources.string(R.string.settings_captcha_solvers) },
          description = { appResources.string(R.string.settings_captcha_solvers_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.CaptchaSolvers, rawSettingKey) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Experimental",
          title = { appResources.string(R.string.settings_experimental_settings) },
          description = { appResources.string(R.string.settings_experimental_settings_description) },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Experimental, rawSettingKey) }
        )
      )
    }
  }

  private suspend fun SettingsScreen.addAboutAppGroup(
    settingActions: SettingActions,
    context: Context
  ) {
    addGroup(
      key = "about_app",
      title = appResources.string(R.string.settings_group_about)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = KurobaSettingKey.Application.AppUpdate.raw,
          title = { createAppVersionString() },
          description = {
            if (AppModuleAndroidUtils.isDevBuild || AppModuleAndroidUtils.isFdroidBuild) {
              appResources.string(R.string.settings_updates_are_disabled)
            } else {
              appResources.string(R.string.settings_update_check)
            }
          },
          callback = {
            when {
              AppModuleAndroidUtils.isDevBuild -> {
                settingActions.showToast(appResources.string(R.string.updater_is_disabled_for_dev_builds))
              }
              AppModuleAndroidUtils.isFdroidBuild -> {
                settingActions.showToast(appResources.string(R.string.updater_is_disabled_for_fdroid_builds))
              }
              else -> {
                kurobaAppUpdateManager.manualUpdateCheck()
              }
            }
          }
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.settings_use_prerelease_builds) },
          setting = kurobaSettings.application.usePrereleaseBuilds
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Changelog",
          title = { appResources.string(R.string.see_changelog_for_this_version) },
          description = { null },
          callback = { settingActions.pushController(ChangelogController(context)) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "Reports",
          enabled = false,
          title = { appResources.string(R.string.settings_report) },
          description = { appResources.string(R.string.settings_report_description) },
          callback = { settingActions.pushController(ReportIssueController(context = context)) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "FindAppOnGithub",
          title = { appResources.string(R.string.settings_find_app_on_github, AndroidUtils.applicationLabel) },
          description = { appResources.string(R.string.settings_find_app_on_github_bottom) },
          callback = { settingActions.openUrl("https://github.com/K1rakishou/Kuroba-Experimental") }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ReportTrackerLink",
          enabled = false,
          title = { appResources.string(R.string.settings_report_tracker_link) },
          description = { appResources.string(R.string.settings_report_tracker_link_description) },
          callback = {
            // no-op
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "AppLicense",
          title = { appResources.string(R.string.settings_about_license) },
          description = { appResources.string(R.string.settings_about_license_description) },
          callback = {
            settingActions.pushController(
              LicensesController(
                context = context,
                title = appResources.string(R.string.settings_about_license),
                url = "file:///android_asset/html/license.html"
              )
            )
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "DeveloperSettings",
          title = { appResources.string(R.string.settings_developer) },
          description = { null },
          callback = { rawSettingKey -> settingActions.openScreen(SettingsScreenKey.Developer, rawSettingKey) }
        )
      )
    }
  }

  private suspend fun createAppVersionString(): String {
    val buildNumber = kurobaSettings.internal.previousBuildNumber.read().coerceAtLeast(0)

    return String.format(
      Locale.ENGLISH,
      "%s %s.%d %s (commit %s)",
      AndroidUtils.applicationLabel.toString(),
      BuildConfig.VERSION_NAME,
      buildNumber,
      getVerificationBadge(),
      BuildConfig.COMMIT_HASH.take(12)
    )
  }

  private fun getVerificationBadge(): String {
    if (AppModuleAndroidUtils.isFdroidBuild) {
      // F-Droid releases are signed by their own keys so the build will always be considered
      // non-official so we just should not show the badge at all.
      return ""
    }

    val verifiedBuildType = AppModuleAndroidUtils.verifiedBuildType()

    val isVerified = verifiedBuildType == AndroidUtils.VerifiedBuildType.Release
      || verifiedBuildType == AndroidUtils.VerifiedBuildType.Debug

    return if (isVerified) {
      "✓"
    } else {
      "✗"
    }
  }
}