package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.UpdateManager
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.filters.FiltersController
import com.github.k1rakishou.chan.features.issues.ReportIssueController
import com.github.k1rakishou.chan.features.settings.AppearanceScreen
import com.github.k1rakishou.chan.features.settings.BehaviorScreen
import com.github.k1rakishou.chan.features.settings.CachingScreen
import com.github.k1rakishou.chan.features.settings.CaptchaSolversScreen
import com.github.k1rakishou.chan.features.settings.DeveloperScreen
import com.github.k1rakishou.chan.features.settings.ExperimentalScreen
import com.github.k1rakishou.chan.features.settings.ImportExportScreen
import com.github.k1rakishou.chan.features.settings.MainScreen
import com.github.k1rakishou.chan.features.settings.MediaScreen
import com.github.k1rakishou.chan.features.settings.PluginsScreen
import com.github.k1rakishou.chan.features.settings.SecurityScreen
import com.github.k1rakishou.chan.features.settings.SettingClickAction
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.WatcherScreen
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.setup.SitesSetupController
import com.github.k1rakishou.chan.ui.controller.LicensesController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getVerifiedBuildType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isFdroidBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.common.AndroidUtils.VerifiedBuildType
import com.github.k1rakishou.common.AndroidUtils.getApplicationLabel

class MainSettingsScreen(
  context: Context,
  private val mainControllerCallbacks: MainControllerCallbacks,
  private val chanFilterManager: ChanFilterManager,
  private val siteManager: SiteManager,
  private val updateManager: UpdateManager,
  private val reportManager: ReportManager,
  private val navigationController: NavigationController,
  private val dialogFactory: DialogFactory
) : BaseSettingsScreen(
  context,
  MainScreen,
  R.string.settings_screen
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup(),
      buildAboutAppGroup()
    )
  }

  private fun buildAboutAppGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MainScreen.AboutAppGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_about),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.AppVersion,
          topDescriptionStringFunc = { createAppVersionString() },
          bottomDescriptionStringFunc = {
            if (isDevBuild() || isFdroidBuild()) {
              context.getString(R.string.settings_updates_are_disabled)
            } else {
              context.getString(R.string.settings_update_check)
            }
          },
          callbackWithClickAction = {
            when {
              isDevBuild() -> SettingClickAction.ShowToast(R.string.updater_is_disabled_for_dev_builds)
              isFdroidBuild() -> SettingClickAction.ShowToast(R.string.updater_is_disabled_for_fdroid_builds)
              else -> {
                updateManager.manualUpdateCheck()
                SettingClickAction.NoAction
              }
            }
          },
          notificationType = SettingNotificationType.ApkUpdate
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.Changelog,
          topDescriptionIdFunc = { R.string.see_changelog_for_this_version },
          callback = {
            val changelogUrl = BuildConfig.GITHUB_CHANGELOGS_ENDPOINT + BuildConfig.VERSION_CODE + ".txt"
            openLink(changelogUrl)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.Reports,
          topDescriptionIdFunc = { R.string.settings_report },
          bottomDescriptionIdFunc = { R.string.settings_report_description },
          callback = {
            val reportProblemController = ReportIssueController(context = context)
            navigationController.pushController(reportProblemController)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.FindAppOnGithub,
          topDescriptionStringFunc = { getString(R.string.settings_find_app_on_github, getApplicationLabel()) },
          bottomDescriptionIdFunc = { R.string.settings_find_app_on_github_bottom },
          callback = { openLink(BuildConfig.GITHUB_ENDPOINT) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.TryKurobaExLite,
          topDescriptionIdFunc = { R.string.settings_try_kuroba_ex_lite },
          bottomDescriptionIdFunc = { R.string.settings_try_kuroba_ex_lite_bottom },
          callback = { openLink("https://github.com/K1rakishou/KurobaExLite") }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.ReportTrackerLink,
          topDescriptionIdFunc = { R.string.settings_report_tracker_link },
          bottomDescriptionIdFunc = { R.string.settings_report_tracker_link_description },
          callback = { openLink(BuildConfig.GITHUB_REPORTS_ENDPOINT) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.AppLicense,
          topDescriptionIdFunc = { R.string.settings_about_license },
          bottomDescriptionIdFunc = { R.string.settings_about_license_description },
          callback = {
            navigationController.pushController(
              LicensesController(context,
                getString(R.string.settings_about_license),
                "file:///android_asset/html/license.html"
              )
            )
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.DeveloperSettings,
          topDescriptionIdFunc = { R.string.settings_developer },
          callbackWithClickAction = { SettingClickAction.OpenScreen(DeveloperScreen) }
        )

        group
      }
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MainScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_screen),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.ThreadAndFilterWatcher,
          topDescriptionIdFunc = { R.string.settings_watch },
          bottomDescriptionIdFunc = { R.string.settings_watch_summary_enabled },
          callbackWithClickAction = { SettingClickAction.OpenScreen(WatcherScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.SitesSetup,
          topDescriptionIdFunc = { R.string.settings_sites },
          bottomDescriptionStringFunc = {
            val sitesCount = siteManager.activeSiteCount()
            getQuantityString(R.plurals.site, sitesCount, sitesCount)
          },
          callback = { navigationController.pushController(SitesSetupController(context)) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Appearance,
          topDescriptionIdFunc = { R.string.settings_appearance },
          bottomDescriptionIdFunc = { R.string.settings_appearance_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(AppearanceScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Behavior,
          topDescriptionIdFunc = { R.string.settings_behavior },
          bottomDescriptionIdFunc = { R.string.settings_behavior_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(BehaviorScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Media,
          topDescriptionIdFunc = { R.string.settings_media },
          bottomDescriptionIdFunc = { R.string.settings_media_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(MediaScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.ImportExport,
          topDescriptionIdFunc = { R.string.settings_import_export },
          bottomDescriptionIdFunc = { R.string.settings_import_export_description },
          callbackWithClickAction = {
            SettingClickAction.OpenScreen(ImportExportScreen)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Filters,
          topDescriptionIdFunc = { R.string.settings_filters },
          bottomDescriptionStringFunc = {
            val filtersCount = chanFilterManager.filtersCount()
            getQuantityString(R.plurals.filter, filtersCount, filtersCount)
          },
          callback = {
            val filtersController = FiltersController(
              context = context,
              chanFilterMutable = null,
              mainControllerCallbacks = mainControllerCallbacks
            )

            navigationController.pushController(filtersController)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Security,
          topDescriptionIdFunc = { R.string.settings_security },
          bottomDescriptionIdFunc = { R.string.settings_security_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(SecurityScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Caching,
          topDescriptionIdFunc = { R.string.settings_caching },
          bottomDescriptionIdFunc = { R.string.settings_caching_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(CachingScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Plugins,
          topDescriptionIdFunc = { R.string.settings_plugins },
          bottomDescriptionIdFunc = { R.string.settings_plugins_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(PluginsScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.CaptchaSolvers,
          topDescriptionIdFunc = { R.string.settings_captcha_solvers },
          bottomDescriptionIdFunc = { R.string.settings_captcha_solvers_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(CaptchaSolversScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Experimental,
          topDescriptionIdFunc = { R.string.settings_experimental_settings },
          bottomDescriptionIdFunc = { R.string.settings_experimental_settings_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(ExperimentalScreen) }
        )

        group
      }
    )
  }

  private fun createAppVersionString(): String {
    return String.format(
      "%s %s %s (commit %s)",
      getApplicationLabel().toString(),
      BuildConfig.VERSION_NAME,
      getVerificationBadge(),
      BuildConfig.COMMIT_HASH.take(12)
    )
  }

  private fun getVerificationBadge(): String {
    if (isFdroidBuild()) {
      // F-Droid releases are signed by their own keys so the build will always be considered
      // non-official so we just should not show the badge at all.
      return ""
    }

    val verifiedBuildType = getVerifiedBuildType()

    val isVerified = verifiedBuildType == VerifiedBuildType.Release
      || verifiedBuildType == VerifiedBuildType.Debug

    return if (isVerified) {
      "✓"
    } else {
      "✗"
    }
  }

}