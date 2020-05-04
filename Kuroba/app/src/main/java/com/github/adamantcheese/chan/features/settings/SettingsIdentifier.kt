package com.github.adamantcheese.chan.features.settings

sealed class SettingsIdentifier(val identifier: String) {
  sealed class Screen(identifier: String) : SettingsIdentifier(identifier) {
    object DeveloperSettingsScreen : Screen("developer_settings_screen")
  }

  sealed class Group(identifier: String) : SettingsIdentifier(identifier) {
    object DeveloperSettingsGroup : Group("developer_settings_group")
  }

  object Developer {
    object ViewLogs : SettingsIdentifier("view_logs")
    object EnableDisableVerboseLogs : SettingsIdentifier("enable_disable_verbose_logs")
    object CrashApp : SettingsIdentifier("crash_the_app")
    object ClearFileCache : SettingsIdentifier("clear_file_cache")
    object ShowDatabaseSummary : SettingsIdentifier("show_database_summary")
    object FilterWatchIgnoreReset : SettingsIdentifier("filter_watch_ignore_reset")
  }

  override fun toString(): String {
    return "SettingsIdentifier(identifier='$identifier')"
  }
}