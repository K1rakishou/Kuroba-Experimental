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

      object Test : MainGroup("test")

      companion object : IGroupIdentifier {
        override fun getScreenIdentifier(): ScreenIdentifier = MainScreen.getScreenIdentifier()
        override fun getGroupIdentifier(): GroupIdentifier = GroupIdentifier("main_group")
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
}