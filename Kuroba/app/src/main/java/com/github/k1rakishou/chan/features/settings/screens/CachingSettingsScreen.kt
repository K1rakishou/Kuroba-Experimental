package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.features.settings.CachingScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.RangeSettingV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.IOUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.util.ChanPostUtils
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CachingSettingsScreen(
  context: Context,
  private val cacheHandler: Lazy<CacheHandler>,
  private val fileCacheV2: FileCacheV2,
  private val appConstants: AppConstants,
  private val dialogFactory: DialogFactory
) : BaseSettingsScreen(
  context,
  CachingScreen,
  R.string.settings_caching
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildCacheSizeSettingGroup(),
      buildDiskCacheSettingsGroup()
    )
  }

  private fun buildDiskCacheSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = CachingScreen.CacheGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupIdentifier = identifier,
          groupTitle = "Disk caches"
        )

        for (cacheFileType in CacheFileType.values()) {
          group += LinkSettingV2.createBuilder(
            context = context,
            identifier = CachingScreen.CacheGroup.ClearFileCache(cacheFileType.name),
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
              AppModuleAndroidUtils.showToast(context, "Cleared ${cacheFileType.name} disk cache")
            }
          )
        }

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.CacheGroup.ClearExoPlayerCache,
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
            AppModuleAndroidUtils.showToast(context, "Cleared exoplayer cache")
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.CacheGroup.ThreadDownloadCacheSize,
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
              titleText = context.getString(R.string.settings_thread_downloader_clear_disk_cache_title),
              descriptionText = context.getString(R.string.settings_thread_downloader_clear_disk_cache_description),
              positiveButtonText = context.getString(R.string.settings_thread_downloader_clear_disk_cache_clear),
              negativeButtonText = context.getString(R.string.settings_thread_downloader_clear_disk_cache_do_not_clear),
              onPositiveButtonClickListener = {
                for (file in appConstants.threadDownloaderCacheDir.listFiles() ?: emptyArray()) {
                  if (!file.deleteRecursively()) {
                    Logger.d(TAG, "Failed to delete ${file.absolutePath}")
                  }
                }

                AppModuleAndroidUtils.showToast(context, "Thread downloader cached cleared")
              }
            )
          }
        )

        group
      })
  }

  private fun buildCacheSizeSettingGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = CachingScreen.MediaCacheSizeGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_media_cache_size),
          groupIdentifier = identifier
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.MediaCacheSizeGroup.NormalCacheSize,
          topDescriptionIdFunc = { R.string.normal_cache_size_title },
          bottomDescriptionIdFunc = { R.string.normal_cache_size_description },
          currentValueStringFunc = { "${ChanSettings.diskCacheSizeMegabytes.get()} MB" },
          requiresRestart = true,
          setting = ChanSettings.diskCacheSizeMegabytes
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.MediaCacheSizeGroup.PrefetchCacheSize,
          topDescriptionIdFunc = { R.string.prefetch_cache_size_title },
          bottomDescriptionIdFunc = { R.string.prefetch_cache_size_description },
          currentValueStringFunc = { "${ChanSettings.prefetchDiskCacheSizeMegabytes.get()} MB" },
          requiresRestart = true,
          setting = ChanSettings.prefetchDiskCacheSizeMegabytes
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.MediaCacheSizeGroup.MediaCacheCleanupRemoveFilesPercent,
          topDescriptionIdFunc = { R.string.media_cache_cleanup_remove_percent },
          bottomDescriptionIdFunc = { R.string.media_cache_cleanup_remove_percent_description },
          currentValueStringFunc = { "${ChanSettings.diskCacheCleanupRemovePercent.get()}%" },
          requiresRestart = true,
          setting = ChanSettings.diskCacheCleanupRemovePercent
        )

        group
      }
    )
  }

  companion object {
    private const val TAG = "CachingSettingsScreen"
  }

}