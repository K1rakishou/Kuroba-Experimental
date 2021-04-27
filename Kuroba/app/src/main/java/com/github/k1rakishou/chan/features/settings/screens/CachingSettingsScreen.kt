package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.CachingScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.RangeSettingV2

class CachingSettingsScreen(
  context: Context
) : BaseSettingsScreen(
  context,
  CachingScreen,
  R.string.settings_caching
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildCacheSizeSettingGroup(),
      buildDatabaseCacheSizeSettingGroup()
    )
  }

  private fun buildDatabaseCacheSizeSettingGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = CachingScreen.DatabaseCacheSizeGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_database_cache_size),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.DatabaseCacheSizeGroup.DatabaseCachingEnabled,
          topDescriptionIdFunc = { R.string.settings_database_post_caching_enabled },
          bottomDescriptionIdFunc = { R.string.settings_database_post_caching_enabled_description },
          setting = ChanSettings.databasePostCachingEnabled,
          requiresRestart = true
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.DatabaseCacheSizeGroup.MaxDatabasePostsCount,
          topDescriptionIdFunc = { R.string.database_max_posts },
          bottomDescriptionIdFunc = { R.string.database_max_posts_description },
          currentValueStringFunc = { ChanSettings.databaseMaxPostsCount.get().toString() },
          requiresRestart = true,
          setting = ChanSettings.databaseMaxPostsCount,
          dependsOnSetting = ChanSettings.databasePostCachingEnabled
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.DatabaseCacheSizeGroup.MaxDatabaseThreadsCount,
          topDescriptionIdFunc = { R.string.database_max_threads },
          bottomDescriptionIdFunc = { R.string.database_max_threads_description },
          currentValueStringFunc = { ChanSettings.databaseMaxThreadsCount.get().toString() },
          requiresRestart = true,
          setting = ChanSettings.databaseMaxThreadsCount,
          dependsOnSetting = ChanSettings.databasePostCachingEnabled
        )

        group += RangeSettingV2.createBuilder(
          context = context,
          identifier = CachingScreen.DatabaseCacheSizeGroup.DatabasePostsCleanupRemovePercent,
          topDescriptionIdFunc = { R.string.database_posts_cleanup_remove_percent },
          bottomDescriptionIdFunc = { R.string.database_posts_cleanup_remove_percent_description },
          currentValueStringFunc = { "${ChanSettings.databasePostsCleanupRemovePercent.get()}%" },
          requiresRestart = true,
          setting = ChanSettings.databasePostsCleanupRemovePercent,
          dependsOnSetting = ChanSettings.databasePostCachingEnabled,
        )

        group
      }
    )
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

}