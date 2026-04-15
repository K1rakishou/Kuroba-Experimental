package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.v2.KurobaSettings
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CachingSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
  private val cacheHandler: CacheHandler,
  private val chunkedMediaDownloader: ChunkedMediaDownloader,
  private val appConstants: AppConstants,
  private val dialogFactory: DialogFactory
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildCacheSizeSettingGroup()
      buildDiskCacheSettingsGroup(context, settingActions)
    }
  }

  private suspend fun SettingsScreen.buildCacheSizeSettingGroup() {
    addGroup(
      key = "media_cache_size",
      title = appResources.string(R.string.settings_media_cache_size)
    ) {
      addSetting(
        SettingUiElement.Range(
          title = { appResources.string(R.string.normal_cache_size_title) },
          description = { appResources.string(R.string.normal_cache_size_description) },
          currentValue = { "${kurobaSettings.application.diskCacheSizeMegabytes.read()} MB" },
          setting = kurobaSettings.application.diskCacheSizeMegabytes,
          requiresAppRestart = true,
        )
      )

      addSetting(
        SettingUiElement.Range(
          title = { appResources.string(R.string.prefetch_cache_size_title) },
          description = { appResources.string(R.string.prefetch_cache_size_description) },
          currentValue = { "${kurobaSettings.application.prefetchDiskCacheSizeMegabytes.read()} MB" },
          setting = kurobaSettings.application.prefetchDiskCacheSizeMegabytes,
          requiresAppRestart = true,
        )
      )

      addSetting(
        SettingUiElement.Range(
          title = { appResources.string(R.string.media_cache_cleanup_remove_percent) },
          description = { appResources.string(R.string.media_cache_cleanup_remove_percent_description) },
          currentValue = { "${kurobaSettings.application.diskCacheCleanupRemovePercent.read()}%" },
          setting = kurobaSettings.application.diskCacheCleanupRemovePercent,
          requiresAppRestart = true,
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildDiskCacheSettingsGroup(
    context: Context,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "disk_cache",
      title = appResources.string(R.string.settings_screen_caches_disk_cache_group)
    ) {
      for (cacheFileType in CacheFileType.entries) {
        addSetting(
          SettingUiElement.Link(
            composeKey = "${cacheFileType.name}_cache",
            title = { appResources.string(R.string.settings_clear_file_cache, cacheFileType.name) },
            description = {
              val internalCacheSizeBytes = cacheHandler.getSize(cacheFileType)
              val internalCacheMaxSizeBytes = cacheHandler.getMaxSize(cacheFileType)
              return@Link appResources.string(
                R.string.settings_clear_file_cache_bottom_description,
                cacheFileType.name,
                ChanPostUtils.getReadableFileSize(internalCacheSizeBytes),
                ChanPostUtils.getReadableFileSize(internalCacheMaxSizeBytes),
              )
            },
            callback = {
              chunkedMediaDownloader.clearCache(cacheFileType)
              settingActions.showToast(
                appResources.string(R.string.settings_screen_caches_cleared_disk_cache, cacheFileType.name)
              )
            }
          )
        )
      }

      addSetting(
        SettingUiElement.Link(
          composeKey = "ClearExoPlayerCache",
          title = { appResources.string(R.string.settings_clear_exo_player_file_cache) },
          description = {
            val exoPlayerCacheSizeBytes = withContext(Dispatchers.Default) {
              IOUtils.calculateDirectoryFilesFullSize(appConstants.exoPlayerCacheDir)
            }

            return@Link appResources.string(
              R.string.settings_clear_exo_player_cache_bottom_description,
              ChanPostUtils.getReadableFileSize(exoPlayerCacheSizeBytes)
            )
          },
          callback = {
            SimpleCache.delete(appConstants.exoPlayerCacheDir, null)
            settingActions.showToast(appResources.string(R.string.settings_cleared_exo_player_file_cache))
          }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ThreadDownloadCacheSize",
          title = { appResources.string(R.string.settings_clear_thread_downloader_disk_cache) },
          description = {
            val threadDownloadCacheSize = withContext(Dispatchers.Default) {
              IOUtils.calculateDirectoryFilesFullSize(appConstants.threadDownloaderCacheDir)
            }
            return@Link appResources.string(
              R.string.settings_thread_download_cache_bottom_description,
              ChanPostUtils.getReadableFileSize(threadDownloadCacheSize)
            )
          },
          callback = {
            dialogFactory.showDialog(
              context = context,
              params = KurobaComposeDialogController.Params(
                title = KurobaComposeDialogController.Text.Id(
                  R.string.settings_thread_downloader_clear_disk_cache_title
                ),
                description = KurobaComposeDialogController.Text.Id(
                  R.string.settings_thread_downloader_clear_disk_cache_description
                ),
                positiveButton = KurobaComposeDialogController.PositiveDialogButton(
                  buttonText = R.string.settings_thread_downloader_clear_disk_cache_clear,
                  onClick = {
                    for (file in appConstants.threadDownloaderCacheDir.listFiles() ?: emptyArray()) {
                      if (!file.deleteRecursively()) {
                        Logger.debug(TAG) { "Failed to delete ${file.absolutePath}" }
                      }
                    }
                    settingActions.showToast(appResources.string(R.string.settings_thread_downloader_cache_cleared))
                  }
                ),
                neutralButton = KurobaComposeDialogController.DialogButton(
                  buttonText = R.string.settings_thread_downloader_clear_disk_cache_do_not_clear
                )
              )
            )
          }
        )
      )
    }
  }

  companion object {
    private const val TAG = "CachingSettingsScreenBuilder"
  }
}