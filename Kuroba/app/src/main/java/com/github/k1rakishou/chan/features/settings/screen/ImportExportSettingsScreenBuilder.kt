package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppRestarter
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.features.download.thread.ThreadDownloadingDelegate
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.delegate.ImportExportSettingsDelegate
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.CoroutineScope

class ImportExportSettingsScreenBuilder(
  private val coroutineScope: CoroutineScope,
  private val appResources: AppResources,
  private val fileChooser: FileChooser,
  private val fileManager: FileManager,
  private val dialogFactory: DialogFactory,
  private val appRestarter: AppRestarter,
  private val importExportRepository: ImportExportRepository,
  private val threadDownloadingDelegate: ThreadDownloadingDelegate
) : SettingsScreenBuilder {

  private val importExportSettingsDelegate by lazy {
    ImportExportSettingsDelegate(
      coroutineScope = coroutineScope,
      appResources = appResources,
      appRestarter = appRestarter,
      fileChooser = fileChooser,
      fileManager = fileManager,
      dialogFactory = dialogFactory,
      importExportRepository = importExportRepository,
      threadDownloadingDelegate = threadDownloadingDelegate
    )
  }

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildMainSettingsGroup(context, settingActions)
    }
  }

  private suspend fun SettingsScreen.buildMainSettingsGroup(context: Context, settingActions: SettingActions) {
    addGroup(
      key = "main",
      title = appResources.string(R.string.import_or_export_settings)
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "ExportSetting",
          title = { appResources.string(R.string.export_settings) },
          description = { appResources.string(R.string.export_settings_to_a_file) },
          callback = { importExportSettingsDelegate.onExportClicked(context, settingActions) }
        )
      )

      addSetting(
        SettingUiElement.Link(
          composeKey = "ImportSetting",
          title = { appResources.string(R.string.import_settings) },
          description = { appResources.string(R.string.import_settings_from_a_file) },
          callback = { importExportSettingsDelegate.onImportClicked(context, settingActions) }
        )
      )
    }
  }
}