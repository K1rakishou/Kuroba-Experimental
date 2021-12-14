package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.cache.CacheFileType
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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.util.ChanPostUtils
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
      buildMainSettingsGroup(),
      buildCacheSettingsGroup()
    )
  }

  private fun buildCacheSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = DeveloperScreen.CacheGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupIdentifier = identifier,
          groupTitle = "Caches"
        )

        for (cacheFileType in CacheFileType.values()) {
          group += LinkSettingV2.createBuilder(
            context = context,
            identifier = DeveloperScreen.CacheGroup.ClearFileCache(cacheFileType.name),
            topDescriptionStringFunc = { context.getString(R.string.settings_clear_file_cache, cacheFileType.name) },
            bottomDescriptionStringFunc = {
              val internalCacheSizeBytes = cacheHandler.get().getSize(cacheFileType)
              val internalCacheMaxSizeBytes = cacheHandler.get().getMaxSize(cacheFileType)

              context.getString(
                R.string.settings_clear_file_cache_bottom_description,
                cacheFileType.name,
                ChanPostUtils.getReadableFileSize(internalCacheSizeBytes),
                ChanPostUtils.getReadableFileSize(internalCacheMaxSizeBytes),
              )
            },
            callback = {
              fileCacheV2.clearCache(cacheFileType)
              showToast(context, "Cleared ${cacheFileType.name} disk cache")
            }
          )
        }

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.CacheGroup.ClearExoPlayerCache,
          topDescriptionStringFunc = { context.getString(R.string.settings_clear_exo_player_file_cache) },
          bottomDescriptionStringFunc = {
            val exoPlayerCacheSizeBytes = withContext(Dispatchers.Default) {
              IOUtils.calculateDirectoryFilesFullSize(appConstants.exoPlayerCacheDir)
            }

            context.getString(
              R.string.settings_clear_exo_player_cache_bottom_description,
              ChanPostUtils.getReadableFileSize(exoPlayerCacheSizeBytes)
            )
          },
          callback = {
            SimpleCache.delete(appConstants.exoPlayerCacheDir, null)
            showToast(context, "Cleared exoplayer cache")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = DeveloperScreen.CacheGroup.ThreadDownloadCacheSize,
          topDescriptionStringFunc = { context.getString(R.string.settings_clear_thread_downloader_disk_cache) },
          bottomDescriptionStringFunc = {
            val threadDownloadCacheSize = withContext(Dispatchers.Default) {
              IOUtils.calculateDirectoryFilesFullSize(appConstants.threadDownloaderCacheDir)
            }

            context.getString(
              R.string.settings_thread_download_cache_bottom_description,
              ChanPostUtils.getReadableFileSize(threadDownloadCacheSize)
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

        group
      })
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
          identifier = DeveloperScreen.MainGroup.ShowDatabaseSummary,
          topDescriptionIdFunc = { R.string.settings_database_summary },
          callbackWithClickAction = {
            SettingClickAction.OpenScreen(DatabaseSummaryScreen)
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

        if (AppModuleAndroidUtils.isDevBuild()) {
          group += BooleanSettingV2.createBuilder(
            context = context,
            identifier = DeveloperScreen.MainGroup.ForceSnow,
            topDescriptionIdFunc = { R.string.settings_force_snow },
            setting = ChanSettings.forceSnowMode,
          )

          group += BooleanSettingV2.createBuilder(
            context = context,
            identifier = DeveloperScreen.MainGroup.ForceChristmas,
            topDescriptionIdFunc = { R.string.settings_force_christmas },
            setting = ChanSettings.forceChristmasMode,
          )
        }

        group
      }
    )
  }

  companion object {
    private const val TAG = "DeveloperSettingsScreen"
  }

}