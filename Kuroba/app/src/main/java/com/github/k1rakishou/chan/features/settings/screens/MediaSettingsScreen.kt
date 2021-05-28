package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.MediaScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2

class MediaSettingsScreen(
  context: Context
) : BaseSettingsScreen(
  context,
  MediaScreen,
  R.string.settings_screen_media
) {

  override suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMediaSavingSettingsGroup(),
      buildVideoSettingsGroup(),
      buildLoadingSettingsGroup(),
      buildMiscSettingsGroup()
    )
  }

  private fun buildMiscSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.MiscGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_misc),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.MiscGroup.AlwaysRandomizeFileNameWhenPickingFiles,
          topDescriptionIdFunc = { R.string.setting_always_randomize_picked_files_names },
          bottomDescriptionIdFunc = { R.string.setting_always_randomize_picked_files_names_description },
          setting = ChanSettings.alwaysRandomizePickedFilesNames,
        )

        group
      }
    )
  }

  private fun buildLoadingSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.LoadingGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_media_loading),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = MediaScreen.LoadingGroup.ImageAutoLoadNetwork,
          setting = ChanSettings.imageAutoLoadNetwork,
          topDescriptionIdFunc = { R.string.setting_image_auto_load },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          itemNameMapper = { item ->
            when (item) {
              ChanSettings.NetworkContentAutoLoadMode.ALL -> {
                context.getString(R.string.setting_image_auto_load_all)
              }
              ChanSettings.NetworkContentAutoLoadMode.WIFI -> {
                context.getString(R.string.setting_image_auto_load_wifi)
              }
              ChanSettings.NetworkContentAutoLoadMode.NONE -> {
                context.getString(R.string.setting_image_auto_load_none)
              }
            }
          }
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = MediaScreen.LoadingGroup.VideoAutoLoadNetwork,
          setting = ChanSettings.videoAutoLoadNetwork,
          topDescriptionIdFunc = { R.string.setting_video_auto_load },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          itemNameMapper = { item ->
            when (item) {
              ChanSettings.NetworkContentAutoLoadMode.ALL -> {
                context.getString(R.string.setting_image_auto_load_all)
              }
              ChanSettings.NetworkContentAutoLoadMode.WIFI -> {
                context.getString(R.string.setting_image_auto_load_wifi)
              }
              ChanSettings.NetworkContentAutoLoadMode.NONE -> {
                context.getString(R.string.setting_image_auto_load_none)
              }
            }
          }
        )

        group += ListSettingV2.createBuilder<ChanSettings.ImageClickPreloadStrategy>(
          context = context,
          identifier = MediaScreen.LoadingGroup.ImageClickPreloadStrategy,
          setting = ChanSettings.imageClickPreloadStrategy,
          topDescriptionIdFunc = { R.string.media_settings_image_click_preload_strategy },
          bottomDescriptionStringFunc = { itemName ->
            context.getString(R.string.media_settings_image_click_preload_strategy_description) +
              "\n\n" + itemName
          },
          items = ChanSettings.ImageClickPreloadStrategy.values().toList(),
          itemNameMapper = { item -> item.name }
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.LoadingGroup.AutoLoadThreadImages,
          topDescriptionIdFunc = { R.string.setting_auto_load_thread_images },
          bottomDescriptionIdFunc = { R.string.setting_auto_load_thread_images_description },
          setting = ChanSettings.prefetchMedia,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.LoadingGroup.ShowPrefetchLoadingIndicator,
          topDescriptionIdFunc = { R.string.setting_show_prefetch_loading_indicator_title },
          setting = ChanSettings.showPrefetchLoadingIndicator,
          dependsOnSetting = ChanSettings.prefetchMedia
        )

        group
      }
    )
  }

  private fun buildVideoSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.VideoGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.setting_video_options),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoAutoLoop,
          topDescriptionIdFunc = { R.string.setting_video_auto_loop },
          bottomDescriptionIdFunc = { R.string.setting_video_auto_loop_description },
          setting = ChanSettings.videoAutoLoop
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoDefaultMuted,
          topDescriptionIdFunc = { R.string.setting_video_default_muted },
          bottomDescriptionIdFunc = { R.string.setting_video_default_muted_description },
          setting = ChanSettings.videoDefaultMuted
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.HeadsetDefaultMuted,
          topDescriptionIdFunc = { R.string.setting_headset_default_muted },
          bottomDescriptionIdFunc = { R.string.setting_headset_default_muted_description },
          setting = ChanSettings.headsetDefaultMuted,
          dependsOnSetting = ChanSettings.videoDefaultMuted
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoOpenExternal,
          topDescriptionIdFunc = { R.string.setting_video_open_external },
          bottomDescriptionIdFunc = { R.string.setting_video_open_external_description },
          setting = ChanSettings.videoOpenExternal
        )

        group
      }
    )
  }

  private fun buildMediaSavingSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.MediaSavingGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.setting_save_location),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier =  MediaScreen.MediaSavingGroup.SaveLocation,
          topDescriptionIdFunc = { R.string.setting_save_location },
          bottomDescriptionIdFunc = { R.string.setting_save_location_description },
          callback = {  }
        )

        group
      }
    )
  }

}