package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.NavigationController
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.ReportManager
import com.github.adamantcheese.chan.core.manager.UpdateManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.settings.*
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.ui.controller.FiltersController
import com.github.adamantcheese.chan.ui.controller.LicensesController
import com.github.adamantcheese.chan.ui.controller.ReportProblemController
import com.github.adamantcheese.chan.ui.controller.SitesSetupController
import com.github.adamantcheese.chan.ui.controller.crashlogs.ReviewCrashLogsController
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.utils.AndroidUtils.*

class MainSettingsScreen(
  context: Context,
  private val databaseManager: DatabaseManager,
  private val updateManager: UpdateManager,
  private val reportManager: ReportManager,
  private val navigationController: NavigationController
) : BaseSettingsScreen(
  context,
  MainScreen,
  R.string.settings_screen
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup(),
      buildAboutAppGroup()
    )
  }

  private fun buildAboutAppGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MainScreen.AboutAppGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_about),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.AppVersion,
          topDescriptionStringFunc = { createAppVersionString() },
          bottomDescriptionIdFunc = { R.string.settings_update_check },
          callback = { updateManager.manualUpdateCheck() },
          notificationType = SettingNotificationType.ApkUpdate
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.Reports,
          topDescriptionIdFunc = { R.string.settings_report },
          bottomDescriptionIdFunc = { R.string.settings_report_description },
          callback = { onReportSettingClick() },
          notificationType = SettingNotificationType.CrashLog
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.CollectCrashReport,
          topDescriptionIdFunc = { R.string.settings_collect_crash_logs },
          bottomDescriptionIdFunc = { R.string.settings_collect_crash_logs_description },
          setting = ChanSettings.collectCrashLogs,
          checkChangedCallback = { isChecked ->
            if (!isChecked) {
              reportManager.deleteAllCrashLogs()
            }
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
          identifier = MainScreen.AboutAppGroup.AppLicenses,
          topDescriptionIdFunc = { R.string.settings_about_licenses },
          bottomDescriptionIdFunc = { R.string.settings_about_licenses_description },
          callback = {
            navigationController.pushController(
              LicensesController(context,
                getString(R.string.settings_about_licenses),
                "file:///android_asset/html/licenses.html"
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

        return group
      }
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MainScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_screen),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.ThreadWatcher,
          topDescriptionIdFunc = { R.string.settings_watch },
          bottomDescriptionIdFunc = { R.string.setting_watch_summary_enabled },
          callbackWithClickAction = { SettingClickAction.OpenScreen(ThreadWatcherScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.SitesSetup,
          topDescriptionIdFunc = { R.string.settings_sites },
          bottomDescriptionStringFunc = {
            val sitesCount = databaseManager.runTask(databaseManager.databaseSiteManager.count).toInt()
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
            val filtersCount = databaseManager.runTask(databaseManager.databaseFilterManager.count).toInt()
            getQuantityString(R.plurals.filter, filtersCount, filtersCount)
          },
          callback = { navigationController.pushController(FiltersController(context)) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Experimental,
          topDescriptionIdFunc = { R.string.settings_experimental_settings_title },
          bottomDescriptionIdFunc = { R.string.settings_experimental_settings_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(ExperimentalScreen) }
        )

        return group
      }
    )
  }

  private fun createAppVersionString(): String {
    val isOfficialCheck = if (getBuildType() == BuildType.Release) {
      "✓"
    } else {
      "✗"
    }

    return getApplicationLabel().toString() + " " + BuildConfig.VERSION_NAME + " " + isOfficialCheck
  }

  private fun onReportSettingClick() {
    fun openReportProblemController() {
      navigationController.pushController(ReportProblemController(context))
    }

    val crashLogsCount: Int = reportManager.countCrashLogs()
    if (crashLogsCount > 0) {
      AlertDialog.Builder(context)
        .setTitle(getString(R.string.settings_report_suggest_sending_logs_title, crashLogsCount))
        .setMessage(R.string.settings_report_suggest_sending_logs_message)
        .setPositiveButton(R.string.settings_report_review_button_text) { _, _ ->
          navigationController.pushController(ReviewCrashLogsController(context))
        }
        .setNeutralButton(R.string.settings_report_review_later_button_text) { _, _ ->
          openReportProblemController()
        }
        .setNegativeButton(R.string.settings_report_delete_all_crash_logs) { _, _ ->
          reportManager.deleteAllCrashLogs()
          openReportProblemController()
        }
        .create()
        .show()

      return
    }

    openReportProblemController()
  }

}