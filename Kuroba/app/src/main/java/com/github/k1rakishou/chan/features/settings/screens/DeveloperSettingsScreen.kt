package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.features.settings.DatabaseSummaryScreen
import com.github.k1rakishou.chan.features.settings.DeveloperScreen
import com.github.k1rakishou.chan.features.settings.SettingClickAction
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import dagger.Lazy

class DeveloperSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val cacheHandler: Lazy<CacheHandler>,
  private val fileCacheV2: FileCacheV2,
  private val themeEngine: ThemeEngine,
  private val appConstants: AppConstants
) : BaseSettingsScreen(
  context,
  DeveloperScreen,
  R.string.settings_developer
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = DeveloperScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ForceLowRamDevice,
          topDescriptionIdFunc = { R.string.settings_force_low_ram_device },
          bottomDescriptionIdFunc = { R.string.settings_force_low_ram_device_description },
          setting = ChanSettings.isLowRamDeviceForced,
          requiresRestart = true
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ViewLogs,
          topDescriptionIdFunc = { R.string.settings_open_logs },
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
          topDescriptionIdFunc = { R.string.settings_crash_app },
          callback = {
            throw RuntimeException("Debug crash")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ClearFileCache,
          topDescriptionStringFunc = { context.getString(R.string.settings_clear_file_cache) },
          bottomDescriptionStringFunc = {
            val cacheSize = cacheHandler.get().getSize() / 1024 / 1024
            context.getString(R.string.settings_clear_file_cache_bottom_description, cacheSize)
          },
          callback = {
            fileCacheV2.clearCache()
            SimpleCache.delete(appConstants.exoPlayerCacheDir, null)

            showToast(context, "Cleared media/exoplayer caches")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ShowDatabaseSummary,
          topDescriptionIdFunc = { R.string.settings_database_summary },
          callbackWithClickAction = {
            SettingClickAction.OpenScreen(DatabaseSummaryScreen)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.DumpThreadStack,
          topDescriptionIdFunc = { R.string.settings_dump_thread_stack },
          callback = {
            dumpThreadStack()
            showToast(context, "Thread stack dumped")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ResetThreadOpenCounter,
          topDescriptionIdFunc = { R.string.settings_reset_thread_open_counter },
          callback = {
            ChanSettings.threadOpenCounter.reset()
            showToast(context, "Done")
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
          topDescriptionIdFunc = { R.string.settings_simulate_app_not_updated },
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

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.AutoThemeSwitcher,
          topDescriptionIdFunc = { R.string.settings_auto_theme_switcher },
          bottomDescriptionStringFunc = {
            val status = if (themeEngine.isAutoThemeSwitcherRunning()) {
              "Running"
            } else {
              "Stopped"
            }

            return@createBuilder getString(R.string.settings_auto_theme_switcher_bottom, status)
          },
          callbackWithClickAction = {
            if (themeEngine.isAutoThemeSwitcherRunning()) {
              themeEngine.stopAutoThemeSwitcher()
            } else {
              themeEngine.startAutoThemeSwitcher()
            }

            return@createBuilder SettingClickAction.RefreshClickedSetting
          }
        )

        group
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