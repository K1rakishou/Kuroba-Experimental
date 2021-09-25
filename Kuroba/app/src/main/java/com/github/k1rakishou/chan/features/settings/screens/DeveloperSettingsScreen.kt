package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.DatabaseSummaryScreen
import com.github.k1rakishou.chan.features.settings.DeveloperScreen
import com.github.k1rakishou.chan.features.settings.SettingClickAction
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.ui.controller.LogsController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.persist_state.PersistableChanState
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeveloperSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val cacheHandler: Lazy<CacheHandler>,
  private val fileCacheV2: FileCacheV2,
  private val themeEngine: ThemeEngine,
  private val appConstants: AppConstants,
  private val dialogFactory: DialogFactory
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

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.CheckUpateApkVersionCode,
          topDescriptionIdFunc = { R.string.settings_check_update_apk_version_code },
          bottomDescriptionIdFunc = { R.string.settings_check_update_apk_version_code_description },
          setting = ChanSettings.checkUpdateApkVersionCode,
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ShowMpvInternalLogs,
          topDescriptionIdFunc = { R.string.settings_check_show_mpv_internal_logs },
          bottomDescriptionIdFunc = { R.string.settings_check_show_mpv_internal_logs_description },
          setting = ChanSettings.showMpvInternalLogs,
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
            val oneMb = 1024L * 1024L

            val exoPlayerCacheSizeBytes = withContext(Dispatchers.Default) {
              IOUtils.calculateDirectoryFilesFullSize(appConstants.exoPlayerCacheDir) / oneMb
            }
            val internalCacheSizeBytes = cacheHandler.get().getSize() / oneMb

            context.getString(
              R.string.settings_clear_file_cache_bottom_description,
              internalCacheSizeBytes,
              exoPlayerCacheSizeBytes
            )
          },
          callback = {
            fileCacheV2.clearCache()
            SimpleCache.delete(appConstants.exoPlayerCacheDir, null)

            showToast(context, "Cleared media/exoplayer caches")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.MainGroup.ThreadDownloadCacheSize,
          topDescriptionStringFunc = { context.getString(R.string.settings_clear_thread_downloader_disk_cache) },
          bottomDescriptionStringFunc = {
            val oneMb = 1024L * 1024L

            val threadDownloadCacheSize = withContext(Dispatchers.Default) {
              IOUtils.calculateDirectoryFilesFullSize(appConstants.threadDownloaderCacheDir) / oneMb
            }

            context.getString(
              R.string.settings_thread_download_cache_bottom_description,
              threadDownloadCacheSize
            )
          },
          callback = {
            dialogFactory.createSimpleConfirmationDialog(
              context = context,
              titleText = getString(R.string.settings_thread_downloader_clear_disk_cache_title),
              descriptionText = getString(R.string.settings_thread_downloader_clear_disk_cache_description),
              positiveButtonText = getString(R.string.settings_thread_downloader_clear_disk_cache_clear),
              negativeButtonText = getString(R.string.settings_thread_downloader_clear_disk_cache_do_not_clear),
              onPositiveButtonClickListener = {
                for (file in appConstants.threadDownloaderCacheDir.listFiles() ?: emptyArray()) {
                  if (!file.deleteRecursively()) {
                    Logger.d(TAG, "Failed to delete ${file.absolutePath}")
                  }
                }

                showToast(context, "Thread downloader cached cleared")
              }
            )
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

        if (isDevBuild()) {
          group += BooleanSettingV2.createBuilder(
            context = context,
            identifier = DeveloperScreen.MainGroup.Force4chanBirthday,
            topDescriptionIdFunc = { R.string.settings_force_4chan_birthday },
            setting = ChanSettings.force4chanBirthdayMode,
          )

          group += BooleanSettingV2.createBuilder(
            context = context,
            identifier = DeveloperScreen.MainGroup.ForceHalloween,
            topDescriptionIdFunc = { R.string.settings_force_halloween },
            setting = ChanSettings.forceHalloweenMode,
          )

        }

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

  companion object {
    private const val TAG = "DeveloperSettingsScreen"
  }

}