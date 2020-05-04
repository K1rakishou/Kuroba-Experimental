package com.github.adamantcheese.chan.features.settings

sealed class SettingsIdentifier(val identifier: String) {
  sealed class Screen(identifier: String) : SettingsIdentifier(identifier) {
    object DeveloperSettingsScreen : Screen("developer_settings_screen")
    object DatabaseSummaryScreen : Screen("database_summary_screen")
  }

  sealed class Group(identifier: String) : SettingsIdentifier(identifier) {
    object DeveloperSettingsGroup : Group("developer_settings_group")
    object DatabaseSummaryGroup : Group("database_summary_group")
  }

  sealed class Developer(identifier: String) : SettingsIdentifier("developer_${identifier}") {
    object ViewLogs : Developer("view_logs")
    object EnableDisableVerboseLogs : Developer("enable_disable_verbose_logs")
    object CrashApp : Developer("crash_the_app")
    object ClearFileCache : Developer("clear_file_cache")
    object ShowDatabaseSummary : Developer("show_database_summary")
    object FilterWatchIgnoreReset : Developer("filter_watch_ignore_reset")
    object DumpThreadStack : Developer("dump_thread_stack")
    object ForceAwakeWakeables : Developer("force_awake_wakeables")
    object ResetThreadOpenCounter : Developer("reset_thread_open_counter")
    object CrashOnSafeThrow : Developer("crash_on_safe_throw")
    object SimulateAppUpdated : Developer("simulate_app_updated")
    object SimulateAppNotUpdated : Developer("simulate_app_not_updated")
  }

  sealed class DatabaseSummary(identifier: String) : SettingsIdentifier("db_summary_${identifier}") {
    object ClearInlinedFilesTable : DatabaseSummary("clear_inlined_files_table")
    object ClearLinkExtraInfoTable : DatabaseSummary("clear_link_info_table")
    object ClearSeenPostsTable : DatabaseSummary("clear_seen_posts_table")
  }

  override fun toString(): String {
    return "SettingsIdentifier(identifier='$identifier')"
  }
}