package com.github.adamantcheese.chan.features.settings

import java.util.*

inline class Identifier(val id: String)
inline class SettingIdentifier(val id: String)
inline class ScreenIdentifier(val id: String)
inline class GroupIdentifier(val id: String)

interface IIdentifiable {
  fun getIdentifier(): Identifier
}

interface IScreen
interface IScreenIdentifier : IIdentifiable {
  fun getScreenIdentifier(): ScreenIdentifier

  override fun getIdentifier(): Identifier {
    return Identifier(getScreenIdentifier().id)
  }
}

interface IGroup
interface IGroupIdentifier : IScreenIdentifier {
  fun getGroupIdentifier(): GroupIdentifier

  override fun getIdentifier(): Identifier {
    val id = String.format(
      Locale.ENGLISH,
      "%s_%s",
      getScreenIdentifier().id,
      getGroupIdentifier().id
    )

    return Identifier(id)
  }
}

sealed class SettingsIdentifier(
  private val screenIdentifier: ScreenIdentifier,
  private val groupIdentifier: GroupIdentifier,
  private val settingsIdentifier: SettingIdentifier
) : IIdentifiable {

  override fun getIdentifier(): Identifier {
    val id = String.format(
      Locale.ENGLISH,
      "%s_%s_%s",
      screenIdentifier.id,
      groupIdentifier.id,
      settingsIdentifier.id
    )

    return Identifier(id)
  }

  override fun toString(): String {
    return getIdentifier().id
  }

}

sealed class MainScreen(
  groupIdentifier: GroupIdentifier,
  settingsIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = MainScreen.getScreenIdentifier()
) : IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) :
    IGroup,
    MainScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ThreadWatcher : MainGroup("thread_watcher")
    object SitesSetup : MainGroup("sites_setup")
    object Appearance : MainGroup("appearance")
    object Behavior : MainGroup("behavior")
    object Media : MainGroup("media")
    object ImportExport : MainGroup("import_export")
    object Filters : MainGroup("filters")
    object Experimental : MainGroup("experimental")

    companion object : IGroupIdentifier {
      override fun getScreenIdentifier(): ScreenIdentifier = MainScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  sealed class AboutAppGroup(
    settingsId: String,
    groupIdentifier: GroupIdentifier = AboutAppGroup.getGroupIdentifier()
  ) :
    IGroup,
    MainScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object AppVersion : AboutAppGroup("app_version")
    object Reports : AboutAppGroup("reports")
    object CollectCrashReport : AboutAppGroup("collect_crash_reports")
    object FindAppOnGithub : AboutAppGroup("find_app_on_github")
    object AppLicenses : AboutAppGroup("app_licenses")
    object DeveloperSettings : AboutAppGroup("developer_settings")

    companion object : IGroupIdentifier {
      override fun getScreenIdentifier(): ScreenIdentifier = MainScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("about_app_group")
    }
  }

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("main_screen")
  }
}

sealed class DeveloperScreen(
  groupIdentifier: GroupIdentifier,
  settingsIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
) : IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingsIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    private val groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) : IGroup,
    DeveloperScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ViewLogs : MainGroup("view_logs")
    object EnableDisableVerboseLogs : MainGroup("enable_disable_verbose_logs")
    object CrashApp : MainGroup("crash_the_app")
    object ClearFileCache : MainGroup("clear_file_cache")
    object ShowDatabaseSummary : MainGroup("show_database_summary")
    object FilterWatchIgnoreReset : MainGroup("filter_watch_ignore_reset")
    object DumpThreadStack : MainGroup("dump_thread_stack")
    object ForceAwakeWakeables : MainGroup("force_awake_wakeables")
    object ResetThreadOpenCounter : MainGroup("reset_thread_open_counter")
    object CrashOnSafeThrow : MainGroup("crash_on_safe_throw")
    object SimulateAppUpdated : MainGroup("simulate_app_updated")
    object SimulateAppNotUpdated : MainGroup("simulate_app_not_updated")

    companion object : IGroupIdentifier {
      override fun getScreenIdentifier(): ScreenIdentifier = DeveloperScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("developer_settings_screen")
  }
}

sealed class DatabaseSummaryScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = DatabaseSummaryScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  sealed class MainGroup(
    settingsId: String,
    private val groupIdentifier: GroupIdentifier = MainGroup.getGroupIdentifier()
  ) :
    IGroup,
    DatabaseSummaryScreen(groupIdentifier, SettingIdentifier(settingsId)) {

    object ClearInlinedFilesTable : MainGroup("clear_inlined_files_table")
    object ClearLinkExtraInfoTable : MainGroup("clear_link_info_table")
    object ClearSeenPostsTable : MainGroup("clear_seen_posts_table")

    companion object : IGroupIdentifier {
      override fun getScreenIdentifier(): ScreenIdentifier = DatabaseSummaryScreen.getScreenIdentifier()
      override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
    }
  }

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("database_summary_screen")
  }
}

sealed class ThreadWatcherScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = ThreadWatcherScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("thread_watcher_screen")
  }
}

sealed class SitesSetupScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = SitesSetupScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("sites_setup_screen")
  }
}

sealed class AppearanceScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = AppearanceScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("appearance_screen")
  }
}

sealed class BehaviorScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = BehaviorScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("behavior_screen")
  }
}

sealed class MediaScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = MediaScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("media_screen")
  }
}

sealed class ImportExportScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = ImportExportScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("import_export_screen")
  }
}

sealed class FiltersExportScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = FiltersExportScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("filters_screen")
  }
}

sealed class ExperimentalSettingsScreen(
  groupIdentifier: GroupIdentifier,
  settingIdentifier: SettingIdentifier,
  private val screenIdentifier: ScreenIdentifier = ExperimentalSettingsScreen.getScreenIdentifier()
) :
  IScreen,
  SettingsIdentifier(screenIdentifier, groupIdentifier, settingIdentifier) {

  companion object : IScreenIdentifier {
    override fun getScreenIdentifier(): ScreenIdentifier = ScreenIdentifier("experimental_settings_screen")
  }
}