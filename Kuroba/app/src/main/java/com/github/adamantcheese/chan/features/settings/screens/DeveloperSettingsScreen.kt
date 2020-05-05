package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.controller.NavigationController
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.manager.FilterWatchManager
import com.github.adamantcheese.chan.core.manager.WakeManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.state.PersistableChanState
import com.github.adamantcheese.chan.features.settings.DatabaseSummaryScreen
import com.github.adamantcheese.chan.features.settings.DeveloperScreen
import com.github.adamantcheese.chan.features.settings.SettingClickAction
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.setting.LinkSettingV2
import com.github.adamantcheese.chan.ui.controller.LogsController
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger

class DeveloperSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val cacheHandler: CacheHandler,
  private val fileCacheV2: FileCacheV2,
  private val filterWatchManager: FilterWatchManager,
  private val wakeManager: WakeManager
) : BaseSettingsScreen(
  context,
  DeveloperScreen,
  R.string.settings_developer
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = DeveloperScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ViewLogs,
          topDescriptionIdFunc = {
            R.string.settings_open_logs
          },
          callback = {
            navigationController.pushController(LogsController(context))
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.EnableDisableVerboseLogs,
          topDescriptionIdFunc = {
            if (ChanSettings.verboseLogs.get()) {
              R.string.settings_disable_verbose_logs
            } else {
              R.string.settings_enable_verbose_logs
            }
          },
          callback = {
            ChanSettings.verboseLogs.setSync(!ChanSettings.verboseLogs.get())
            (context as StartActivity).restartApp()
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.CrashApp,
          topDescriptionIdFunc = {
            R.string.settings_crash_app
          },
          callback = {
            throw RuntimeException("Debug crash")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ClearFileCache,
          topDescriptionStringFunc = {
            context.getString(R.string.settings_clear_file_cache)
          },
          bottomDescriptionStringFunc = {
            val cacheSize = cacheHandler.getSize() / 1024 / 1024
            context.getString(R.string.settings_clear_file_cache_bottom_description, cacheSize)
          },
          callback = {
            fileCacheV2.clearCache()
            AndroidUtils.showToast(context, "Cleared image cache")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ShowDatabaseSummary,
          topDescriptionIdFunc = {
            R.string.settings_database_summary
          },
          callbackWithClickAction = {
            SettingClickAction.OpenScreen(DatabaseSummaryScreen)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.FilterWatchIgnoreReset,
          topDescriptionIdFunc = {
            R.string.settings_clear_ignored_filter_watches
          },
          callback = {
            filterWatchManager.clearFilterWatchIgnores()
            AndroidUtils.showToast(context, "Cleared ignores")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.DumpThreadStack,
          topDescriptionIdFunc = {
            R.string.settings_dump_thread_stack
          },
          callback = {
            dumpThreadStack()
            AndroidUtils.showToast(context, "Thread stack dumped")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ForceAwakeWakeables,
          topDescriptionIdFunc = {
            R.string.settings_force_wake_manager_wake
          },
          callback = {
            wakeManager.forceWake()
            AndroidUtils.showToast(context, "Woke all wakeables")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ResetThreadOpenCounter,
          topDescriptionIdFunc = {
            R.string.settings_reset_thread_open_counter
          },
          callback = {
            ChanSettings.threadOpenCounter.reset()
            AndroidUtils.showToast(context, "Done")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.CrashOnSafeThrow,
          topDescriptionIdFunc = {
            if (ChanSettings.crashOnSafeThrow.get()) {
              R.string.settings_crash_on_safe_throw_enabled
            } else {
              R.string.settings_crash_on_safe_throw_disabled
            }
          },
          callback = {
            ChanSettings.crashOnSafeThrow.set(!ChanSettings.crashOnSafeThrow.get())
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.SimulateAppUpdated,
          topDescriptionIdFunc = {
            R.string.settings_simulate_app_updated
          },
          bottomDescriptionIdFunc = {
            R.string.settings_simulate_app_updated_bottom
          },
          callback = {
            PersistableChanState.previousDevHash.setSync(ChanSettings.NO_HASH_SET)
            PersistableChanState.updateCheckTime.setSync(0L)
            PersistableChanState.hasNewApkUpdate.setSync(false)
            (context as StartActivity).restartApp()
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.SimulateAppNotUpdated,
          topDescriptionIdFunc = {
            R.string.settings_simulate_app_not_updated
          },
          bottomDescriptionIdFunc = {
            R.string.settings_simulate_app_not_updated_bottom
          },
          callback = {
            PersistableChanState.previousDevHash.setSync(BuildConfig.COMMIT_HASH)
            PersistableChanState.updateCheckTime.setSync(0L)
            PersistableChanState.hasNewApkUpdate.setSync(true)
            (context as StartActivity).restartApp()
          }
        )

        return group
      }
    )
  }

  private fun dumpThreadStack() {
    val activeThreads: Set<Thread> = Thread.getAllStackTraces().keys
    Logger.i("STACKDUMP-COUNT", activeThreads.size.toString())

    for (t in activeThreads) {
      // ignore these threads as they aren't relevant (main will always be this button press)
      if (t.name.equals("main", ignoreCase = true)
        || t.name.contains("Daemon")
        || t.name.equals("Signal Catcher", ignoreCase = true)
        || t.name.contains("hwuiTask")
        || t.name.contains("Binder:")
        || t.name.equals("RenderThread", ignoreCase = true)
        || t.name.contains("maginfier pixel")
        || t.name.contains("Jit thread")
        || t.name.equals("Profile Saver", ignoreCase = true)
        || t.name.contains("Okio")
        || t.name.contains("AsyncTask")
      ) {
        continue
      }

      val elements = t.stackTrace
      Logger.i("STACKDUMP-HEADER", "Thread: " + t.name)

      for (e in elements) {
        Logger.i("STACKDUMP", e.toString())
      }

      Logger.i("STACKDUMP-FOOTER", "----------------")
    }
  }

}