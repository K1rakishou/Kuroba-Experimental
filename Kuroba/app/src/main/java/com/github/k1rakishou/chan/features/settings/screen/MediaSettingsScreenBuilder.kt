package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.v2.KurobaSettings
import com.github.k1rakishou.v2.parameters.NetworkContentAutoLoadMode
import com.github.k1rakishou.v2.settings.AbstractKurobaSetting

class MediaSettingsScreenBuilder(
  private val kurobaSettings: KurobaSettings,
  private val appResources: AppResources,
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildLoadingSettingsGroup()
      buildMiscSettingsGroup()
    }
  }

  private suspend fun SettingsScreen.buildLoadingSettingsGroup() {
    addGroup(
      key = "loading",
      title = appResources.string(R.string.settings_group_media_loading)
    ) {
      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_image_auto_load) },
          items = NetworkContentAutoLoadMode.entries,
          itemNameMapper = { loadMode ->
            when (loadMode) {
              NetworkContentAutoLoadMode.All -> appResources.string(R.string.setting_image_auto_load_all)
              NetworkContentAutoLoadMode.Unmetered -> appResources.string(R.string.setting_image_auto_load_unmetered)
              NetworkContentAutoLoadMode.None -> appResources.string(R.string.setting_image_auto_load_none)
            }
          },
          setting = kurobaSettings.application.imageAutoLoadNetwork
            as AbstractKurobaSetting<NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>
        )
      )

      addSetting(
        SettingUiElement.Items(
          title = { appResources.string(R.string.setting_video_auto_load) },
          items = NetworkContentAutoLoadMode.entries,
          itemNameMapper = { loadMode ->
            when (loadMode) {
              NetworkContentAutoLoadMode.All -> appResources.string(R.string.setting_image_auto_load_all)
              NetworkContentAutoLoadMode.Unmetered -> appResources.string(R.string.setting_image_auto_load_unmetered)
              NetworkContentAutoLoadMode.None -> appResources.string(R.string.setting_image_auto_load_none)
            }
          },
          setting = kurobaSettings.application.videoAutoLoadNetwork
            as AbstractKurobaSetting<NetworkContentAutoLoadMode, NetworkContentAutoLoadMode>
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildMiscSettingsGroup() {
    addGroup(
      key = "misc",
      title = appResources.string(R.string.settings_group_misc)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "alwaysRandomizePickedFilesNames",
          enabled = false,
          title = { appResources.string(R.string.setting_always_randomize_picked_files_names) },
          description = { "Setting was moved into the reply layout settings (three dot menu)" },
          deprecated = true,
          callback = {}
        )
      )
    }
  }

}